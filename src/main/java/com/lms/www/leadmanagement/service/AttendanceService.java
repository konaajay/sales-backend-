package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.*;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceDailyRepository dailyRepository;
    private final OfficeLocationRepository officeRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final AttendanceShiftRepository shiftRepository;
    private final WfhRequestRepository wfhRequestRepository;


    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private LocalDateTime nowInIndia() { return LocalDateTime.now(INDIA_ZONE); }
    private LocalDate todayInIndia() { return LocalDate.now(INDIA_ZONE); }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0 || lon1 == 0) return 0;
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public boolean isWfhApproved(Long userId) {
        LocalDateTime now = nowInIndia();
        return wfhRequestRepository.findLatestApprovedByUserId(userId).stream()
                .anyMatch(w -> w.isActiveOn(now));
    }

    @Transactional
    public AttendanceDTO clockIn(LocationRequestDTO request, String ua, String ip) {
        Long userId = request.getUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Optional<AttendanceSession> active = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE));
        if (active.isPresent()) throw new RuntimeException("You already have an active session.");

        boolean wfh = isWfhApproved(userId);
        
        // Strategic: Prioritize the user's assigned office
        OfficeLocation assigned = user.getAssignedOffice();
        final OfficeLocation office;
        
        // Fallback to closest if no office assigned
        if (assigned == null) {
            office = officeRepository.findAll().stream()
                .min(Comparator.comparingDouble(o -> calculateDistance(request.getLat(), request.getLng(), o.getLatitude(), o.getLongitude())))
                .orElseThrow(() -> new RuntimeException("No office locations configured and user has no assigned office."));
        } else {
            office = assigned;
        }

        double distance = calculateDistance(request.getLat(), request.getLng(), office.getLatitude(), office.getLongitude());
        if (!wfh && distance > office.getRadius()) {
            String distStr = distance > 1000 ? String.format("%.2f km", distance/1000.0) : String.format("%.0f meters", distance);
            throw new RuntimeException("PUNCH DENIED: You are " + distStr + " away from the office radius (" + office.getRadius() + "m). You do not have WFH approval.");
        }

        LocalDateTime now = nowInIndia();
        LocalTime shiftStart = (user.getShift() != null) ? user.getShift().getStartTime() : LocalTime.of(9, 30);
        int grace = (user.getShift() != null) ? user.getShift().getGraceMinutes() : 0;
        
        boolean isLate = now.toLocalTime().isAfter(shiftStart.plusMinutes(grace));
        int lateMins = 0;
        if (isLate) {
            long totalLateMins = Duration.between(shiftStart, now.toLocalTime()).toMinutes();
            long breakOverlapMins = calculateBreakOverlap(shiftStart, now.toLocalTime(), user.getShift());
            lateMins = (int) Math.max(0, totalLateMins - breakOverlapMins);
        }

        AttendanceSession session = AttendanceSession.builder()
                .user(user).office(office).checkInTime(now).status(AttendanceStatus.WORKING)
                .lastLat(request.getLat()).lastLng(request.getLng()).lastSeenTime(now)
                .isLate(isLate).lateMinutes(lateMins).build();
        
        session = sessionRepository.save(session);
        return mapToDTO(session, todayInIndia());
    }

    @Transactional
    public AttendanceDTO trackLocation(LocationRequestDTO request, String ua, String ip) {
        AttendanceSession session = sessionRepository.findActiveSession(request.getUserId(), List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE))
                .orElseThrow(() -> new ResourceNotFoundException("No active session."));

        LocalDateTime now = nowInIndia();
        User user = session.getUser();
        AttendanceShift shift = user.getShift();

        boolean wfh = isWfhApproved(session.getUser().getId());
        boolean inside = wfh || calculateDistance(request.getLat(), request.getLng(), session.getOffice().getLatitude(), session.getOffice().getLongitude()) <= session.getOffice().getRadius();

        LocalTime shiftStart = (shift != null) ? shift.getStartTime() : LocalTime.of(9, 30);
        LocalTime shiftEnd = (shift != null) ? shift.getEndTime() : LocalTime.of(18, 30);
        
        LocalDateTime shiftEndDateTime = session.getCheckInTime().toLocalDate().atTime(shiftEnd);
        if (shiftEnd.isBefore(shiftStart)) {
            shiftEndDateTime = shiftEndDateTime.plusDays(1);
        }
        
        // Strategic: Force checkout if more than 30 mins after shift end OR if shift ended and user is outside
        if (now.isAfter(shiftEndDateTime.plusMinutes(30)) || (now.isAfter(shiftEndDateTime) && !inside)) {
            finalizeSession(session, now, true);
            return mapToDTO(session, todayInIndia());
        } 
        resolveStateAndAccumulateTime(session, shift, now, inside);
        session.setLastLat(request.getLat());
        session.setLastLng(request.getLng());
        session.setLastSeenTime(now);
        
        AttendanceSession savedSession = sessionRepository.save(session);
        return mapToDTO(savedSession, todayInIndia());
    }

    private void resolveStateAndAccumulateTime(AttendanceSession session, AttendanceShift shift, LocalDateTime now, boolean inside) {
        LocalDateTime lastPing = session.getLastSeenTime();
        long segmentSecs = Duration.between(lastPing, now).toSeconds();
        if (segmentSecs <= 0) return;

        AttendanceStatus current = session.getStatus();

        if (current == AttendanceStatus.WORKING) {
            session.setTotalWorkSeconds((session.getTotalWorkSeconds() != null ? session.getTotalWorkSeconds() : 0L) + segmentSecs);
        } else if (current == AttendanceStatus.AUTO_BREAK) {
            LocalTime pingTime = lastPing.toLocalTime();
            if (isInsideLongBreakWindow(pingTime, shift)) {
                session.setLongBreakSeconds((session.getLongBreakSeconds() != null ? session.getLongBreakSeconds() : 0L) + segmentSecs);
            } else if (isInsideShortBreakWindow(pingTime, shift)) {
                session.setShortBreakSeconds((session.getShortBreakSeconds() != null ? session.getShortBreakSeconds() : 0L) + segmentSecs);
            } else {
                session.setTotalBreakSeconds((session.getTotalBreakSeconds() != null ? session.getTotalBreakSeconds() : 0L) + segmentSecs); // fallback
            }
        } else if (current == AttendanceStatus.ON_BREAK) {
            session.setTotalBreakSeconds((session.getTotalBreakSeconds() != null ? session.getTotalBreakSeconds() : 0L) + segmentSecs);
        } else if (current == AttendanceStatus.OUTSIDE) {
            session.setTotalOutsideSeconds((session.getTotalOutsideSeconds() != null ? session.getTotalOutsideSeconds() : 0L) + segmentSecs);
        }

        if (current == AttendanceStatus.ON_SHORT_BREAK || current == AttendanceStatus.ON_LONG_BREAK || current == AttendanceStatus.ON_BREAK) return;

        LocalTime time = now.toLocalTime();
        if (isInsideAutoBreakWindow(time, shift)) {
            session.setStatus(AttendanceStatus.AUTO_BREAK);
        } else if (!inside) {
            session.setStatus(AttendanceStatus.OUTSIDE);
        } else {
            session.setStatus(AttendanceStatus.WORKING);
        }
    }

    private boolean isInsideLongBreakWindow(LocalTime time, AttendanceShift shift) {
        LocalTime lStart = (shift != null && shift.getLongBreakStartTime() != null) ? shift.getLongBreakStartTime() : LocalTime.of(13, 0);
        LocalTime lEnd = (shift != null && shift.getLongBreakEndTime() != null) ? shift.getLongBreakEndTime() : LocalTime.of(14, 0);
        return lStart != null && lEnd != null && !time.isBefore(lStart) && time.isBefore(lEnd);
    }

    private boolean isInsideShortBreakWindow(LocalTime time, AttendanceShift shift) {
        LocalTime sStart = (shift != null && shift.getShortBreakStartTime() != null) ? shift.getShortBreakStartTime() : LocalTime.of(17, 0);
        LocalTime sEnd = (shift != null && shift.getShortBreakEndTime() != null) ? shift.getShortBreakEndTime() : LocalTime.of(17, 10);
        return sStart != null && sEnd != null && !time.isBefore(sStart) && time.isBefore(sEnd);
    }

    private boolean isInsideAutoBreakWindow(LocalTime time, AttendanceShift shift) {
        return isInsideLongBreakWindow(time, shift) || isInsideShortBreakWindow(time, shift);
    }

    @Transactional
    public AttendanceDTO clockOut(Long userId) {
        AttendanceSession session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE))
                .orElseThrow(() -> new ResourceNotFoundException("No active session."));
        finalizeSession(session, nowInIndia(), false);
        return mapToDTO(session, todayInIndia());
    }

    @Transactional
    public AttendanceDTO startBreak(Long userId, String type) {
        AttendanceSession session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE))
                .orElseThrow(() -> new ResourceNotFoundException("No active session."));
        session.setStatus(AttendanceStatus.ON_BREAK);
        session = sessionRepository.save(session);
        return mapToDTO(session, todayInIndia());
    }

    @Transactional
    public AttendanceDTO endBreak(Long userId) {
        AttendanceSession session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.ON_BREAK))
                .orElseThrow(() -> new ResourceNotFoundException("No active break session."));
        session.setStatus(AttendanceStatus.WORKING);
        session = sessionRepository.save(session);
        return mapToDTO(session, todayInIndia());
    }

    @Transactional
    public Optional<AttendanceDTO> getCurrentStatus(Long userId) {
        Optional<AttendanceSession> session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE));
        
        List<OfficeLocation> offices = officeRepository.findAll();
        
        if (session.isPresent()) {
            return Optional.of(mapToDTO(session.get(), todayInIndia()));
        } else {
            User user = userRepository.findById(userId).orElse(null);
            OfficeLocation assigned = (user != null) ? user.getAssignedOffice() : null;
            final OfficeLocation office;

            if (assigned == null) {
                // Strategic: Fallback to closest if no office explicitly assigned
                AttendanceSession lastSession = sessionRepository.findFirstByUserIdOrderByCheckInTimeDesc(userId).orElse(null);
                double lastLat = lastSession != null ? lastSession.getLastLat() : 0;
                double lastLng = lastSession != null ? lastSession.getLastLng() : 0;

                office = offices.stream()
                        .min(Comparator.comparingDouble(o -> calculateDistance(lastLat, lastLng, o.getLatitude(), o.getLongitude())))
                        .orElse(null);
            } else {
                office = assigned;
            }

            return Optional.of(AttendanceDTO.builder()
                    .userId(userId)
                    .status("NOT_STARTED")
                    .officeLat(office != null ? office.getLatitude() : null)
                    .officeLng(office != null ? office.getLongitude() : null)
                    .officeRadius(office != null ? office.getRadius() : 30.0)
                    .officeName(office != null ? office.getName() : null)
                    .isWfhApproved(isWfhApproved(userId))
                    .wfhStatus(isWfhApproved(userId) ? "APPROVED" : "NONE")
                    .build());
        }
    }

    @Transactional
    public void finalizeSession(AttendanceSession session, LocalDateTime now, boolean auto) {
        session.setStatus(AttendanceStatus.PUNCHED_OUT);
        session.setCheckOutTime(now);
        session.setAutoCheckout(auto);
        sessionRepository.save(session);

        LocalDate date = session.getCheckInTime().toLocalDate();
        AttendanceDaily daily = dailyRepository.findFirstByUserIdAndDate(session.getUser().getId(), date)
                .orElse(AttendanceDaily.builder().user(session.getUser()).date(date).build());
        
        daily.setLoginTime(session.getCheckInTime());
        daily.setLogoutTime(now);
        daily.setTotalWorkMinutes((int)((session.getTotalWorkSeconds() != null ? session.getTotalWorkSeconds() : 0) / 60));
        daily.setTotalBreakMinutes((int)((session.getTotalBreakSeconds() != null ? session.getTotalBreakSeconds() : 0) / 60));
        daily.setShortBreakMinutes((int)((session.getShortBreakSeconds() != null ? session.getShortBreakSeconds() : 0) / 60));
        daily.setLongBreakMinutes((int)((session.getLongBreakSeconds() != null ? session.getLongBreakSeconds() : 0) / 60));
        daily.setTotalOutsideMinutes((int)((session.getTotalOutsideSeconds() != null ? session.getTotalOutsideSeconds() : 0) / 60));
        daily.setLate(session.isLate());
        daily.setLateMinutes(session.getLateMinutes());
        
        long workMins = session.getTotalWorkSeconds() / 60;
        User user = session.getUser();
        AttendanceShift shift = user.getShift();

        int minFullDay = 480;
        int minHalfDay = 240;

        if (shift != null) {
            minFullDay = shift.getMinFullDayMinutes();
            minHalfDay = shift.getMinHalfDayMinutes();
        }

        if (workMins >= minFullDay) daily.setStatus("PRESENT");
        else if (workMins >= minHalfDay) daily.setStatus("HALF_DAY");
        else daily.setStatus("SHORT_DAY");
        
        dailyRepository.save(daily);
    }

    @Transactional
    public List<AttendanceDTO> getLogs(LocalDate start, LocalDate end, Long targetUserId, Long teamId, Long managerId, User requester) {
        Set<Long> userIds = securityService.getScopedUserIds(requester, managerId, teamId);
        if (targetUserId != null && targetUserId > 0) {
            securityService.validateAccess(requester, targetUserId);
            userIds = Set.of(targetUserId);
        }
        
        LocalDate today = LocalDate.now(INDIA_ZONE);
        LocalDate effectiveEnd = (end == null || end.isAfter(today)) ? today : end;

        List<AttendanceDTO> results = new ArrayList<>();
        for (Long uid : userIds) {
            User user = userRepository.findById(uid).orElse(null);
            if (user == null) continue;

            LocalDate joinDate = user.getJoiningDate();
            for (LocalDate date = start; !date.isAfter(effectiveEnd); date = date.plusDays(1)) {
                if (joinDate != null && date.isBefore(joinDate)) continue;
                results.add(fetchAttendanceForDate(user, date));
            }
        }
        return results;
    }

    @Transactional
    public List<AttendanceDTO> getMyLogs(Long userId, LocalDate from, LocalDate to) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate today = LocalDate.now(INDIA_ZONE);
        LocalDate effectiveEnd = (to == null || to.isAfter(today)) ? today : to;
        
        List<AttendanceDTO> results = new ArrayList<>();
        LocalDate joinDate = user.getJoiningDate();
        for (LocalDate date = from; !date.isAfter(effectiveEnd); date = date.plusDays(1)) {
            if (joinDate != null && date.isBefore(joinDate)) continue;
            results.add(fetchAttendanceForDate(user, date));
        }
        return results;
    }

    private AttendanceDTO fetchAttendanceForDate(User user, LocalDate date) {
        Optional<AttendanceSession> session = sessionRepository.findSessionsForDate(user.getId(), date.atStartOfDay(), date.atTime(23, 59, 59)).stream().findFirst();
        if (session.isPresent()) return mapToDTO(session.get(), date);
        
        return dailyRepository.findSingleByUserIdAndDate(user.getId(), date)
                .map(d -> mapDailyToDTO(d, user, date))
                .orElse(createAbsentDTO(user, date));
    }

    private AttendanceDTO mapToDTO(AttendanceSession s, LocalDate date) {
        String note = dailyRepository.findSingleByUserIdAndDate(s.getUser().getId(), date)
                .map(AttendanceDaily::getNote)
                .orElse(null);

        return AttendanceDTO.builder()
                .userId(s.getUser().getId()).userName(s.getUser().getName())
                .date(date).checkInTime(s.getCheckInTime()).checkOutTime(s.getCheckOutTime())
                .status(s.getStatus().name())
                .totalWorkMinutes((int)((s.getTotalWorkSeconds() != null ? s.getTotalWorkSeconds() : 0)/60))
                .totalBreakMinutes((int)((s.getTotalBreakSeconds() != null ? s.getTotalBreakSeconds() : 0)/60))
                .shortBreakMinutes((int)((s.getShortBreakSeconds() != null ? s.getShortBreakSeconds() : 0)/60))
                .longBreakMinutes((int)((s.getLongBreakSeconds() != null ? s.getLongBreakSeconds() : 0)/60))
                .totalIdleMinutes((int)((s.getTotalOutsideSeconds() != null ? s.getTotalOutsideSeconds() : 0)/60))
                .late(s.isLate()).lateMinutes(s.getLateMinutes()).isAutoCheckout(s.isAutoCheckout())
                .lastLat(s.getLastLat()).lastLng(s.getLastLng()).lastSeenTime(s.getLastSeenTime())
                .officeLat(s.getOffice() != null ? s.getOffice().getLatitude() : null)
                .officeLng(s.getOffice() != null ? s.getOffice().getLongitude() : null)
                .officeRadius(s.getOffice() != null ? s.getOffice().getRadius() : 30.0)
                .officeName(s.getOffice() != null ? s.getOffice().getName() : null)
                .isWfhApproved(isWfhApproved(s.getUser().getId()))
                .wfhStatus(isWfhApproved(s.getUser().getId()) ? "APPROVED" : "NONE")
                .note(note)
                .build();
    }

    private AttendanceDTO mapDailyToDTO(AttendanceDaily d, User user, LocalDate date) {
        OfficeLocation office = user.getAssignedOffice();
        if (office == null) {
            office = officeRepository.findAll().stream().findFirst().orElse(null);
        }
        return AttendanceDTO.builder()
                .userId(user.getId()).userName(user.getName())
                .date(date).checkInTime(d.getLoginTime()).checkOutTime(d.getLogoutTime())
                .status(d.getStatus()).totalWorkMinutes(d.getTotalWorkMinutes())
                .totalBreakMinutes(d.getTotalBreakMinutes() != null ? d.getTotalBreakMinutes() : 0)
                .shortBreakMinutes(d.getShortBreakMinutes() != null ? d.getShortBreakMinutes() : 0)
                .longBreakMinutes(d.getLongBreakMinutes() != null ? d.getLongBreakMinutes() : 0)
                .totalIdleMinutes(d.getTotalOutsideMinutes() != null ? d.getTotalOutsideMinutes() : 0)
                .late(d.isLate()).lateMinutes(d.getLateMinutes())
                .officeLat(office != null ? office.getLatitude() : null)
                .officeLng(office != null ? office.getLongitude() : null)
                .officeRadius(office != null ? office.getRadius() : 30.0)
                .officeName(office != null ? office.getName() : null)
                .note(d.getNote())
                .isWfhApproved(isWfhApproved(user.getId()))
                .wfhStatus(isWfhApproved(user.getId()) ? "APPROVED" : "NONE")
                .build();
    }

    private AttendanceDTO createAbsentDTO(User user, LocalDate date) {
        OfficeLocation office = user.getAssignedOffice();
        if (office == null) {
            office = officeRepository.findAll().stream().findFirst().orElse(null);
        }
        return AttendanceDTO.builder()
                .userId(user.getId()).userName(user.getName())
                .date(date).status("ABSENT")
                .officeLat(office != null ? office.getLatitude() : null)
                .officeLng(office != null ? office.getLongitude() : null)
                .officeRadius(office != null ? office.getRadius() : 30.0)
                .officeName(office != null ? office.getName() : null)
                .isWfhApproved(isWfhApproved(user.getId()))
                .wfhStatus(isWfhApproved(user.getId()) ? "APPROVED" : "NONE")
                .build();
    }

    private long calculateBreakOverlap(LocalTime start, LocalTime end, AttendanceShift shift) {
        long overlapMins = 0;
        
        // 1. Determine Long Break (Lunch) Window
        LocalTime lStart = (shift != null && shift.getLongBreakStartTime() != null) ? shift.getLongBreakStartTime() : LocalTime.of(13, 0);
        LocalTime lEnd = (shift != null && shift.getLongBreakEndTime() != null) ? shift.getLongBreakEndTime() : LocalTime.of(14, 0);
        
        if (lStart != null && lEnd != null) {
            overlapMins += getIntervalOverlap(start, end, lStart, lEnd);
        }
        
        // 2. Determine Short Break Window
        LocalTime sStart = (shift != null && shift.getShortBreakStartTime() != null) ? shift.getShortBreakStartTime() : LocalTime.of(17, 0);
        LocalTime sEnd = (shift != null && shift.getShortBreakEndTime() != null) ? shift.getShortBreakEndTime() : LocalTime.of(17, 10);
        
        if (sStart != null && sEnd != null) {
            overlapMins += getIntervalOverlap(start, end, sStart, sEnd);
        }
        
        return overlapMins;
    }

    private long getIntervalOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        if (start1.isAfter(end2) || end1.isBefore(start2)) return 0;
        LocalTime maxStart = start1.isAfter(start2) ? start1 : start2;
        LocalTime minEnd = end1.isBefore(end2) ? end1 : end2;
        return Duration.between(maxStart, minEnd).toMinutes();
    }

    public List<AttendanceShift> getAllShifts() {
        return shiftRepository.findAll();
    }

    // Compatibility methods for dashboard/reports

    
    @Transactional(readOnly = true)
    public List<AttendanceDTO> getDailySummaries(Long mId, Long tId, Long uId, LocalDate from, LocalDate to) {
        User requester = securityService.getCurrentUser();
        if (requester == null) return new ArrayList<>();
        
        // Re-fetch within transaction to avoid LazyInitializationException
        requester = userRepository.findById(requester.getId()).orElse(requester);
        
        if (from == null) from = LocalDate.now(INDIA_ZONE);
        if (to == null) to = LocalDate.now(INDIA_ZONE);
        return getLogs(from, to, uId, tId, mId, requester);
    }

    @Transactional
    public void updateDailyNote(Long userId, LocalDate date, String note) {
        AttendanceDaily daily = dailyRepository.findFirstByUserIdAndDate(userId, date)
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                return AttendanceDaily.builder()
                    .user(user)
                    .date(date)
                    .build();
            });
        daily.setNote(note);
        dailyRepository.save(daily);
    }
    
    @Transactional(readOnly = true)
    public AttendancePreviewResponse calculatePreview(AttendancePreviewRequest request) {
        if (request.getUserId() == null || request.getDate() == null) {
            throw new IllegalArgumentException("User ID and Date are required");
        }
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        AttendanceShift shift = user.getShift();
        
        String reqStatus = request.getStatus();
        if (reqStatus != null && (reqStatus.equalsIgnoreCase("HOLIDAY") || reqStatus.equalsIgnoreCase("LEAVE") || reqStatus.equalsIgnoreCase("ABSENT"))) {
            return AttendancePreviewResponse.builder()
                    .workedMinutes(0)
                    .breakMinutes(0)
                    .effectiveMinutes(0)
                    .status(reqStatus.toUpperCase())
                    .isLate(false)
                    .build();
        }

        int minFullDay = shift != null ? shift.getMinFullDayMinutes() : 480;
        int minHalfDay = shift != null ? shift.getMinHalfDayMinutes() : 240;
        LocalTime shiftStart = shift != null ? shift.getStartTime() : LocalTime.of(9, 30);
        int grace = shift != null ? shift.getGraceMinutes() : 0;

        LocalDateTime login = request.getLoginTime();
        LocalDateTime logout = request.getLogoutTime();

        long workedMinutes = 0;
        long breakMinutes = 0;
        boolean isLate = false;
        String status = "ABSENT";

        if (login != null && logout != null) {
            long totalMinutes = Duration.between(login, logout).toMinutes();
            breakMinutes = request.getBreakMinutes() != null 
                    ? request.getBreakMinutes() 
                    : calculateBreakOverlap(login.toLocalTime(), logout.toLocalTime(), shift);
            workedMinutes = Math.max(0, totalMinutes - breakMinutes);

            if (request.getWorkMinutes() != null) {
                workedMinutes = request.getWorkMinutes();
            }

            isLate = login.toLocalTime().isAfter(shiftStart.plusMinutes(grace)) || "LATE".equalsIgnoreCase(reqStatus);

            if (reqStatus != null && !reqStatus.trim().isEmpty() && !reqStatus.equalsIgnoreCase("AUTO")) {
                status = reqStatus.toUpperCase();
            } else {
                if (workedMinutes >= minFullDay) {
                    status = "PRESENT";
                } else if (workedMinutes >= minHalfDay) {
                    status = "HALF_DAY";
                } else {
                    status = "SHORT_DAY";
                }
            }
        } else {
            // Time-less entry
            if (reqStatus != null && !reqStatus.trim().isEmpty() && !reqStatus.equalsIgnoreCase("AUTO")) {
                status = reqStatus.toUpperCase();
            } else {
                status = "ABSENT";
            }
            isLate = "LATE".equalsIgnoreCase(status);
            if (status.equals("PRESENT")) {
                workedMinutes = minFullDay;
            } else if (status.equals("HALF_DAY")) {
                workedMinutes = minHalfDay;
            } else if (status.equals("SHORT_DAY")) {
                workedMinutes = minHalfDay / 2;
            } else if (status.equals("LATE")) {
                workedMinutes = minFullDay;
            }
        }

        return AttendancePreviewResponse.builder()
                .workedMinutes(workedMinutes)
                .breakMinutes(breakMinutes)
                .effectiveMinutes(workedMinutes)
                .status(status)
                .isLate(isLate)
                .build();
    }

    @Transactional
    public void saveManualEntry(AttendancePreviewRequest request) {
        if (request.getUserId() == null || request.getDate() == null) {
            throw new IllegalArgumentException("User ID and Date are required");
        }
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        AttendancePreviewResponse preview = calculatePreview(request);
        String status = preview.getStatus();
        
        // Find existing sessions on that date
        List<AttendanceSession> sessions = sessionRepository.findSessionsForDate(
                user.getId(), request.getDate().atStartOfDay(), request.getDate().atTime(23, 59, 59));
        
        if (status.equals("ABSENT") || status.equals("HOLIDAY") || status.equals("LEAVE") || request.getLoginTime() == null || request.getLogoutTime() == null) {
            // Delete existing sessions so we fall back to daily record
            sessionRepository.deleteAll(sessions);
            
            // Create or update daily record
            AttendanceDaily daily = dailyRepository.findFirstByUserIdAndDate(user.getId(), request.getDate())
                    .orElseGet(() -> AttendanceDaily.builder().user(user).date(request.getDate()).build());
            daily.setLoginTime(null);
            daily.setLogoutTime(null);
            daily.setTotalWorkMinutes((int) preview.getWorkedMinutes());
            daily.setTotalBreakMinutes(0);
            daily.setShortBreakMinutes(0);
            daily.setLongBreakMinutes(0);
            daily.setTotalOutsideMinutes(0);
            daily.setLate(preview.isLate());
            daily.setLateMinutes(preview.isLate() ? 30 : 0);
            daily.setStatus(status);
            dailyRepository.save(daily);
        } else {
            // It is PRESENT, HALF_DAY, SHORT_DAY, or other active status with times
            AttendanceSession session = sessions.isEmpty() ? new AttendanceSession() : sessions.get(0);
            session.setUser(user);
            if (session.getOffice() == null) {
                session.setOffice(user.getAssignedOffice() != null ? user.getAssignedOffice() : officeRepository.findAll().stream().findFirst().orElse(null));
            }
            session.setCheckInTime(request.getLoginTime());
            session.setCheckOutTime(request.getLogoutTime());
            session.setStatus(AttendanceStatus.PUNCHED_OUT);
            session.setLastSeenTime(request.getLogoutTime());
            
            long totalSeconds = request.getLoginTime() != null && request.getLogoutTime() != null
                    ? Duration.between(request.getLoginTime(), request.getLogoutTime()).toSeconds()
                    : 0L;
            long breakSeconds = preview.getBreakMinutes() * 60;
            session.setTotalWorkSeconds(Math.max(0, totalSeconds - breakSeconds));
            session.setTotalBreakSeconds(breakSeconds);
            session.setLate(preview.isLate());
            
            LocalTime shiftStart = user.getShift() != null ? user.getShift().getStartTime() : LocalTime.of(9, 30);
            if (preview.isLate() && request.getLoginTime() != null) {
                session.setLateMinutes((int) Duration.between(shiftStart, request.getLoginTime().toLocalTime()).toMinutes());
            } else {
                session.setLateMinutes(0);
            }
            sessionRepository.save(session);

            // Update or create daily record
            AttendanceDaily daily = dailyRepository.findFirstByUserIdAndDate(user.getId(), request.getDate())
                    .orElseGet(() -> AttendanceDaily.builder().user(user).date(request.getDate()).build());
            daily.setLoginTime(request.getLoginTime());
            daily.setLogoutTime(request.getLogoutTime());
            daily.setTotalWorkMinutes((int) preview.getWorkedMinutes());
            daily.setTotalBreakMinutes((int) preview.getBreakMinutes());
            daily.setLate(preview.isLate());
            daily.setLateMinutes(session.getLateMinutes());
            daily.setStatus(status);
            dailyRepository.save(daily);
        }
    }
}
