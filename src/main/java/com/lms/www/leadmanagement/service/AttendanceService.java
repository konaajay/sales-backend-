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
    private final AttendancePolicyRepository policyRepository;
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
        
        Optional<AttendanceSession> active = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE));
        if (active.isPresent()) throw new RuntimeException("You already have an active session.");

        boolean wfh = isWfhApproved(userId);
        OfficeLocation office = officeRepository.findAll().stream()
                .min(Comparator.comparingDouble(o -> calculateDistance(request.getLat(), request.getLng(), o.getLatitude(), o.getLongitude())))
                .orElseThrow(() -> new RuntimeException("No office locations configured."));

        double distance = calculateDistance(request.getLat(), request.getLng(), office.getLatitude(), office.getLongitude());
        if (!wfh && distance > office.getRadius()) {
            String distStr = distance > 1000 ? String.format("%.2f km", distance/1000.0) : String.format("%.0f meters", distance);
            throw new RuntimeException("PUNCH DENIED: You are " + distStr + " away from the office radius (" + office.getRadius() + "m). You do not have WFH approval.");
        }

        AttendancePolicy policy = policyRepository.findByOfficeId(office.getId()).orElseGet(() -> AttendancePolicy.builder().office(office).build());
        
        LocalDateTime now = nowInIndia();
        LocalTime shiftStart = (user.getShift() != null) ? user.getShift().getStartTime() : policy.getShiftStartTime();
        int grace = (user.getShift() != null) ? user.getShift().getGraceMinutes() : (policy.getGracePeriodMinutes() != null ? policy.getGracePeriodMinutes() : 0);
        
        boolean isLate = now.toLocalTime().isAfter(shiftStart.plusMinutes(grace));
        int lateMins = 0;
        if (isLate) {
            long totalLateMins = Duration.between(shiftStart, now.toLocalTime()).toMinutes();
            long breakOverlapMins = calculateBreakOverlap(shiftStart, now.toLocalTime(), policy, user.getShift());
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
        AttendanceSession session = sessionRepository.findActiveSession(request.getUserId(), List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE))
                .orElseThrow(() -> new ResourceNotFoundException("No active session."));

        LocalDateTime now = nowInIndia();
        User user = session.getUser();
        AttendanceShift shift = user.getShift();
        AttendancePolicy policy = policyRepository.findByOfficeId(session.getOffice().getId()).orElseGet(() -> AttendancePolicy.builder().office(session.getOffice()).build());

        boolean wfh = isWfhApproved(session.getUser().getId());
        boolean inside = wfh || calculateDistance(request.getLat(), request.getLng(), session.getOffice().getLatitude(), session.getOffice().getLongitude()) <= session.getOffice().getRadius();

        LocalTime shiftEnd = (shift != null) ? shift.getEndTime() : (policy.getShiftEndTime() != null ? policy.getShiftEndTime() : LocalTime.of(18, 30));
        
        // Strategic: Force checkout if more than 30 mins after shift end OR if shift ended and user is outside
        if (now.toLocalTime().isAfter(shiftEnd.plusMinutes(30)) || (now.toLocalTime().isAfter(shiftEnd) && !inside)) {
            finalizeSession(session, now, true);
            return mapToDTO(session, todayInIndia());
        } 
        resolveStateAndAccumulateTime(session, policy, shift, now, inside);
        session.setLastLat(request.getLat());
        session.setLastLng(request.getLng());
        session.setLastSeenTime(now);
        
        AttendanceSession savedSession = sessionRepository.save(session);
        return mapToDTO(savedSession, todayInIndia());
    }

    private void resolveStateAndAccumulateTime(AttendanceSession session, AttendancePolicy policy, AttendanceShift shift, LocalDateTime now, boolean inside) {
        LocalDateTime lastPing = session.getLastSeenTime();
        long segmentSecs = Duration.between(lastPing, now).toSeconds();
        if (segmentSecs <= 0) return;

        AttendanceStatus current = session.getStatus();

        if (current == AttendanceStatus.WORKING) {
            session.setTotalWorkSeconds(session.getTotalWorkSeconds() + segmentSecs);
        } else if (current == AttendanceStatus.ON_BREAK || current == AttendanceStatus.AUTO_BREAK) {
            session.setTotalBreakSeconds(session.getTotalBreakSeconds() + segmentSecs);
        } else if (current == AttendanceStatus.OUTSIDE) {
            session.setTotalOutsideSeconds(session.getTotalOutsideSeconds() + segmentSecs);
        }

        if (current == AttendanceStatus.ON_BREAK) return;

        LocalTime time = now.toLocalTime();
        if (isInsideAutoBreakWindow(time, policy, shift)) {
            session.setStatus(AttendanceStatus.AUTO_BREAK);
        } else if (!inside) {
            session.setStatus(AttendanceStatus.OUTSIDE);
        } else {
            session.setStatus(AttendanceStatus.WORKING);
        }
    }

    private boolean isInsideAutoBreakWindow(LocalTime time, AttendancePolicy policy, AttendanceShift shift) {
        LocalTime lStart = (shift != null && shift.getLongBreakStartTime() != null) ? shift.getLongBreakStartTime() : (policy != null ? policy.getLongBreakStartTime() : null);
        LocalTime lEnd = (shift != null && shift.getLongBreakEndTime() != null) ? shift.getLongBreakEndTime() : (policy != null ? policy.getLongBreakEndTime() : null);
        LocalTime sStart = (shift != null && shift.getShortBreakStartTime() != null) ? shift.getShortBreakStartTime() : (policy != null ? policy.getShortBreakStartTime() : null);
        LocalTime sEnd = (shift != null && shift.getShortBreakEndTime() != null) ? shift.getShortBreakEndTime() : (policy != null ? policy.getShortBreakEndTime() : null);

        if (lStart != null && lEnd != null && !time.isBefore(lStart) && time.isBefore(lEnd)) return true;
        if (sStart != null && sEnd != null && !time.isBefore(sStart) && time.isBefore(sEnd)) return true;
        return false;
    }

    @Transactional
    public AttendanceDTO clockOut(Long userId) {
        AttendanceSession session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE))
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
        AttendanceSession session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.ON_BREAK))
                .orElseThrow(() -> new ResourceNotFoundException("No active break session."));
        session.setStatus(AttendanceStatus.WORKING);
        session = sessionRepository.save(session);
        return mapToDTO(session, todayInIndia());
    }

    @Transactional
    public Optional<AttendanceDTO> getCurrentStatus(Long userId) {
        Optional<AttendanceSession> session = sessionRepository.findActiveSession(userId, List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_BREAK, AttendanceStatus.AUTO_BREAK, AttendanceStatus.OUTSIDE));
        
        if (session.isPresent()) {
            return Optional.of(mapToDTO(session.get(), todayInIndia()));
        } else {
            // Return off-duty status but WITH office info for distance calculation
            OfficeLocation office = officeRepository.findAll().stream().findFirst().orElse(null);
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
        AttendanceDaily daily = dailyRepository.findSingleByUserIdAndDate(session.getUser().getId(), date)
                .orElse(AttendanceDaily.builder().user(session.getUser()).date(date).build());
        
        daily.setLoginTime(session.getCheckInTime());
        daily.setLogoutTime(now);
        daily.setTotalWorkMinutes((int)(session.getTotalWorkSeconds() / 60));
        daily.setLate(session.isLate());
        daily.setLateMinutes(session.getLateMinutes());
        
        long workMins = session.getTotalWorkSeconds() / 60;
        if (workMins >= 480) daily.setStatus("PRESENT");
        else if (workMins >= 240) daily.setStatus("HALF_DAY");
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
        return AttendanceDTO.builder()
                .userId(s.getUser().getId()).userName(s.getUser().getName())
                .date(date).checkInTime(s.getCheckInTime()).checkOutTime(s.getCheckOutTime())
                .status(s.getStatus().name()).totalWorkMinutes((int)(s.getTotalWorkSeconds()/60))
                .totalBreakMinutes((int)(s.getTotalBreakSeconds()/60)).totalIdleMinutes((int)(s.getTotalOutsideSeconds()/60))
                .late(s.isLate()).lateMinutes(s.getLateMinutes()).isAutoCheckout(s.isAutoCheckout())
                .lastLat(s.getLastLat()).lastLng(s.getLastLng()).lastSeenTime(s.getLastSeenTime())
                .officeLat(s.getOffice() != null ? s.getOffice().getLatitude() : null)
                .officeLng(s.getOffice() != null ? s.getOffice().getLongitude() : null)
                .officeRadius(s.getOffice() != null ? s.getOffice().getRadius() : 30.0)
                .officeName(s.getOffice() != null ? s.getOffice().getName() : null)
                .isWfhApproved(isWfhApproved(s.getUser().getId()))
                .wfhStatus(isWfhApproved(s.getUser().getId()) ? "APPROVED" : "NONE")
                .build();
    }

    private AttendanceDTO mapDailyToDTO(AttendanceDaily d, User user, LocalDate date) {
        return AttendanceDTO.builder()
                .userId(user.getId()).userName(user.getName())
                .date(date).checkInTime(d.getLoginTime()).checkOutTime(d.getLogoutTime())
                .status(d.getStatus()).totalWorkMinutes(d.getTotalWorkMinutes())
                .late(d.isLate()).lateMinutes(d.getLateMinutes())
                .build();
    }

    private AttendanceDTO createAbsentDTO(User user, LocalDate date) {
        return AttendanceDTO.builder()
                .userId(user.getId()).userName(user.getName())
                .date(date).status("ABSENT").build();
    }

    private long calculateBreakOverlap(LocalTime start, LocalTime end, AttendancePolicy policy, AttendanceShift shift) {
        long overlapMins = 0;
        
        // 1. Determine Long Break (Lunch) Window
        LocalTime lStart = (shift != null && shift.getLongBreakStartTime() != null) ? shift.getLongBreakStartTime() : (policy != null ? policy.getLongBreakStartTime() : LocalTime.of(13, 0));
        LocalTime lEnd = (shift != null && shift.getLongBreakEndTime() != null) ? shift.getLongBreakEndTime() : (policy != null ? policy.getLongBreakEndTime() : LocalTime.of(14, 0));
        
        if (lStart != null && lEnd != null) {
            overlapMins += getIntervalOverlap(start, end, lStart, lEnd);
        }
        
        // 2. Determine Short Break Window
        LocalTime sStart = (shift != null && shift.getShortBreakStartTime() != null) ? shift.getShortBreakStartTime() : (policy != null ? policy.getShortBreakStartTime() : null);
        LocalTime sEnd = (shift != null && shift.getShortBreakEndTime() != null) ? shift.getShortBreakEndTime() : (policy != null ? policy.getShortBreakEndTime() : null);
        
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

    public void updateDailyNote(Long userId, LocalDate date, String note) {}
    
    public AttendancePreviewResponse calculatePreview(AttendancePreviewRequest request) { return new AttendancePreviewResponse(); }
    public void saveManualEntry(AttendancePreviewRequest request) {}
}
