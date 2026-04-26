package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.LocationRequestDTO;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.exception.SecurityViolationException;
import com.lms.www.leadmanagement.mapper.AttendanceMapper;
import com.lms.www.leadmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceDailyRepository attendanceDailyRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final AttendancePolicyRepository attendancePolicyRepository;
    private final UserRepository userRepository;
    private final AttendanceMapper attendanceMapper;
    private final GlobalTargetRepository globalTargetRepository;
    private final AttendanceAuditLogRepository auditLogRepository;
    private final SecurityService securityService;
    private final AttendanceShiftRepository attendanceShiftRepository;

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private double maxSpeedKmph = 150.0;
    private double maxAccuracyMeters = 50.0;
    private double velocityJumpThresholdMeters = 500.0;

    private List<OfficeLocation> officeCache = null;
    private LocalDateTime lastCacheRefresh = null;

    private static final int DEFAULT_TRACKING_INTERVAL = 300;
    private static final int DEFAULT_GRACE_PERIOD = 2;
    private static final LocalTime DEFAULT_SHORT_BREAK_START = LocalTime.of(17, 0);
    private static final LocalTime DEFAULT_SHORT_BREAK_END = LocalTime.of(17, 10);
    private static final LocalTime DEFAULT_LONG_BREAK_START = LocalTime.of(13, 0);
    private static final LocalTime DEFAULT_LONG_BREAK_END = LocalTime.of(14, 0);

    private static final List<AttendanceStatus> ACTIVE_STATUSES = List.of(
            AttendanceStatus.WORKING,
            AttendanceStatus.ON_SHORT_BREAK,
            AttendanceStatus.ON_LONG_BREAK,
            AttendanceStatus.AUTO_BREAK,
            AttendanceStatus.OUTSIDE_UNAUTHORIZED
    );

    private LocalDateTime nowInIndia() {
        return LocalDateTime.now(INDIA_ZONE);
    }

    private LocalDate todayInIndia() {
        return LocalDate.now(INDIA_ZONE);
    }

    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private synchronized List<OfficeLocation> getOffices() {
        if (officeCache == null || lastCacheRefresh == null
                || lastCacheRefresh.isBefore(LocalDateTime.now().minusMinutes(5))) {
            officeCache = officeLocationRepository.findAll();
            lastCacheRefresh = LocalDateTime.now();
        }
        return officeCache;
    }

    private Optional<OfficeLocation> findNearestOffice(double lat, double lng) {
        return getOffices().stream()
                .min((o1, o2) -> Double.compare(
                        calculateDistance(lat, lng, o1.getLatitude(), o1.getLongitude()),
                        calculateDistance(lat, lng, o2.getLatitude(), o2.getLongitude())));
    }

    @Transactional
    public AttendanceDTO clockIn(LocationRequestDTO request, String ua, String ip) {
        Long userId = request.getUserId();

        if (request.isMockLocation()) {
            throw new SecurityViolationException("Security violation: Mock location detected.");
        }

        attendanceSessionRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .ifPresent(this::finalizeSession);

        OfficeLocation office = findNearestOffice(request.getLat(), request.getLng())
                .orElseThrow(() -> new RuntimeException("No office locations defined."));

        if (calculateDistance(request.getLat(), request.getLng(), office.getLatitude(), office.getLongitude()) > office
                .getRadius()) {
            throw new RuntimeException("Outside office zone. Move closer to " + office.getName());
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Safeguard: Prevent clock-in before joining date
        if (user.getJoiningDate() != null && todayInIndia().isBefore(user.getJoiningDate())) {
            throw new RuntimeException("Cannot clock in before your official joining date: " + user.getJoiningDate());
        }

        AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(office.getId())
                .orElseGet(() -> AttendancePolicy.builder().office(office).build());

        LocalDateTime now = nowInIndia();
        boolean isLate = now.toLocalTime().isAfter(policy.getShiftStartTime()
                .plusMinutes(policy.getGracePeriodMinutes() != null ? policy.getGracePeriodMinutes() : 0));

        AttendanceSession session = AttendanceSession.builder()
                .user(user).office(office).checkInTime(now).status(AttendanceStatus.WORKING)
                .lastLat(request.getLat()).lastLng(request.getLng())
                .lastAccuracy(request.getAccuracy() != null ? request.getAccuracy() : 0.0)
                .lastLocationTime(now).lastSeenTime(now)
                .deviceId(request.getDeviceId() != null ? request.getDeviceId() : "WEB_BROWSER")
                .userAgent(ua).ipHash(secureHash(ip))
                .totalWorkMinutes(0).totalBreakMinutes(0).isAutoCheckout(false)
                .isLate(isLate)
                .build();

        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Transactional
    public AttendanceDTO trackLocation(LocationRequestDTO request, String ua, String ip) {
        Long userId = request.getUserId();
        if (request.isMockLocation())
            throw new SecurityViolationException("Mock location detected.");

        try {
            AttendanceSession session = attendanceSessionRepository
                    .findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(userId, ACTIVE_STATUSES)
                    .orElseThrow(() -> new ResourceNotFoundException("No active session found."));

            AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(session.getOffice().getId())
                    .orElseGet(() -> AttendancePolicy.builder().office(session.getOffice()).build());

            LocalDateTime now = nowInIndia();
            performVelocityCheck(session, request, now);

            long secondsSinceLast = Duration.between(session.getLastLocationTime(), now).getSeconds();
            int interval = policy.getTrackingIntervalSec() != null ? policy.getTrackingIntervalSec()
                    : DEFAULT_TRACKING_INTERVAL;
            if (secondsSinceLast < (interval / 2))
                return convertToDTO(session, session.getCheckInTime().toLocalDate());

            resolveAttendanceState(session, policy, now, isInsideOffice(session, request));

            session.setLastLat(request.getLat());
            session.setLastLng(request.getLng());
            session.setLastAccuracy(request.getAccuracy() != null ? request.getAccuracy() : session.getLastAccuracy());
            session.setLastLocationTime(now);

            return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
        } catch (PessimisticLockingFailureException e) {
            throw new RuntimeException("System busy. Try again.");
        }
    }

    private boolean isInsideOffice(AttendanceSession session, LocationRequestDTO request) {
        User user = session.getUser();
        // Check assigned office first (highest probability)
        if (user.getAssignedOffice() != null) {
            double dist = calculateDistance(request.getLat(), request.getLng(), user.getAssignedOffice().getLatitude(),
                    user.getAssignedOffice().getLongitude());
            if (dist <= user.getAssignedOffice().getRadius())
                return true;
        }
        // Check other offices from cache
        return getOffices().stream().anyMatch(o -> calculateDistance(request.getLat(), request.getLng(),
                o.getLatitude(), o.getLongitude()) <= o.getRadius());
    }

    private void performVelocityCheck(AttendanceSession session, LocationRequestDTO request, LocalDateTime now) {
        if (request.getAccuracy() != null && request.getAccuracy() > maxAccuracyMeters)
            throw new RuntimeException("Inaccurate location data.");

        if (session.getLastLat() != null && session.getLastLocationTime() != null) {
            double metersMoved = calculateDistance(session.getLastLat(), session.getLastLng(), request.getLat(),
                    request.getLng());
            long secondsElapsed = Duration.between(session.getLastLocationTime(), now).toSeconds();

            if (secondsElapsed > 0) {
                double kmph = (metersMoved / 1000.0) / (secondsElapsed / 3600.0);
                if (kmph > maxSpeedKmph)
                    throw new RuntimeException("Suspicious activity detected.");
                if (secondsElapsed < 10 && metersMoved > velocityJumpThresholdMeters)
                    throw new RuntimeException("Sudden location jump detected.");
            }
        }
    }

    private long calculateOverlapSeconds(LocalDateTime start, LocalDateTime end, LocalTime targetStart,
            LocalTime targetEnd) {
        if (targetStart == null || targetEnd == null)
            return 0;

        LocalDateTime tStart = end.toLocalDate().atTime(targetStart);
        LocalDateTime tEnd = end.toLocalDate().atTime(targetEnd);

        LocalDateTime overlapStart = start.isAfter(tStart) ? start : tStart;
        LocalDateTime overlapEnd = end.isBefore(tEnd) ? end : tEnd;

        if (overlapStart.isBefore(overlapEnd)) {
            return Duration.between(overlapStart, overlapEnd).toSeconds();
        }
        return 0;
    }

    private long getBreakOverlap(LocalDateTime start, LocalDateTime end, AttendancePolicy policy) {
        LocalTime lStart = policy.getLongBreakStartTime() != null ? policy.getLongBreakStartTime()
                : DEFAULT_LONG_BREAK_START;
        LocalTime lEnd = policy.getLongBreakEndTime() != null ? policy.getLongBreakEndTime() : DEFAULT_LONG_BREAK_END;
        LocalTime sStart = policy.getShortBreakStartTime() != null ? policy.getShortBreakStartTime()
                : DEFAULT_SHORT_BREAK_START;
        LocalTime sEnd = policy.getShortBreakEndTime() != null ? policy.getShortBreakEndTime()
                : DEFAULT_SHORT_BREAK_END;

        return calculateOverlapSeconds(start, end, lStart, lEnd) + calculateOverlapSeconds(start, end, sStart, sEnd);
    }

    private void resolveAttendanceState(AttendanceSession session, AttendancePolicy policy, LocalDateTime now,
            boolean currentlyInside) {
        if (session.getLastLocationTime() == null) {
            return; // no data -> don't assume
        }

        AttendanceStatus oldStatus = session.getStatus();
        LocalDateTime lastPing = session.getLastSeenTime() != null ? session.getLastSeenTime()
                : (session.getCheckInTime() != null ? session.getCheckInTime() : now.minusMinutes(1));

        long segmentSecs = Duration.between(lastPing, now).toSeconds();
        if (segmentSecs <= 0)
            return;

        int interval = policy.getTrackingIntervalSec() != null ? policy.getTrackingIntervalSec()
                : DEFAULT_TRACKING_INTERVAL;

        if (segmentSecs > interval * 2L) {
            return; // treat as UNKNOWN, don't count
        }

        long breakSecs = getBreakOverlap(lastPing, now, policy);
        long remainingSecs = segmentSecs - breakSecs;
        if (remainingSecs < 0)
            remainingSecs = 0;

        // 1. Break Logic (Exact Overlap)
        if (breakSecs > 0) {
            session.setTotalBreakSeconds(session.getTotalBreakSeconds() + breakSecs);
        }

        // 2. Work vs Outside Logic (Strict Separation)
        if (remainingSecs > 0) {
            if (currentlyInside) {
                session.setTotalWorkSeconds(session.getTotalWorkSeconds() + remainingSecs);
            } else {
                session.setUnauthorizedOutsideSeconds(session.getUnauthorizedOutsideSeconds() + remainingSecs);
                if (session.getOutsideStartTime() == null) {
                    session.setOutsideStartTime(now.minusSeconds(remainingSecs)); // Estimate when they went outside
                }
            }
        }

        // 3. Reset state if back inside
        if (currentlyInside) {
            session.setOutsideStartTime(null);
        }

        session.setTotalWorkMinutes((int) (session.getTotalWorkSeconds() / 60));
        session.setTotalBreakMinutes((int) (session.getTotalBreakSeconds() / 60));
        session.setLastSeenTime(now);

        updateVisualStatus(session, policy, now, currentlyInside, oldStatus);
    }

    private void updateVisualStatus(AttendanceSession session, AttendancePolicy policy, LocalDateTime now,
            boolean inside, AttendanceStatus old) {
        LocalTime currentTime = now.toLocalTime();
        if (isTimeInBreak(currentTime, policy)) {
            session.setStatus(isLongBreak(currentTime, policy) ? AttendanceStatus.ON_LONG_BREAK
                    : AttendanceStatus.ON_SHORT_BREAK);
        } else if (inside) {
            session.setStatus(AttendanceStatus.WORKING);
        } else {
            int graceSecs = (policy.getGracePeriodMinutes() != null ? policy.getGracePeriodMinutes()
                    : DEFAULT_GRACE_PERIOD) * 60;
            if (session.getOutsideStartTime() != null
                    && Duration.between(session.getOutsideStartTime(), now).toSeconds() > graceSecs) {
                session.setStatus(AttendanceStatus.OUTSIDE_UNAUTHORIZED);
            } else {
                // Visually show as working during grace period, but seconds are already
                // securely bucketed to Outside
                session.setStatus(AttendanceStatus.WORKING);
            }
        }
    }

    private boolean isTimeInBreak(LocalTime time, AttendancePolicy policy) {
        return isWithin(time, policy.getLongBreakStartTime(), policy.getLongBreakEndTime(), DEFAULT_LONG_BREAK_START,
                DEFAULT_LONG_BREAK_END) ||
                isWithin(time, policy.getShortBreakStartTime(), policy.getShortBreakEndTime(),
                        DEFAULT_SHORT_BREAK_START, DEFAULT_SHORT_BREAK_END);
    }

    private boolean isLongBreak(LocalTime time, AttendancePolicy policy) {
        return isWithin(time, policy.getLongBreakStartTime(), policy.getLongBreakEndTime(), DEFAULT_LONG_BREAK_START,
                DEFAULT_LONG_BREAK_END);
    }

    private boolean isWithin(LocalTime current, LocalTime start, LocalTime end, LocalTime dStart, LocalTime dEnd) {
        LocalTime s = start != null ? start : dStart;
        LocalTime e = end != null ? end : dEnd;
        // Fix: Exclude end time to prevent overlap (e.g., 14:00 is end of lunch, 14:00
        // is working)
        return !current.isBefore(s) && current.isBefore(e);
    }

    private String secureHash(String input) {
        if (input == null)
            return "0";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Transactional(readOnly = true)
    public List<AttendanceDTO> getDailySummaries(LocalDate startDate, LocalDate endDate, Long targetUserId,
            Long requesterId) {
        User requester = userRepository.findById(requesterId).orElseThrow();
        List<Long> visibleUserIds = new ArrayList<>();

        if (securityService.isAdmin(requester)) {
            if (targetUserId != null)
                visibleUserIds.add(targetUserId);
            else
                userRepository.findAll().stream().filter(u -> !securityService.isAdmin(u))
                        .forEach(u -> visibleUserIds.add(u.getId()));
        } else {
            List<Long> subordinates = userRepository.findSubordinateIds(requesterId);
            if (targetUserId != null) {
                if (subordinates.contains(targetUserId) || requesterId.equals(targetUserId))
                    visibleUserIds.add(targetUserId);
            } else {
                visibleUserIds.addAll(subordinates);
                visibleUserIds.add(requesterId);
            }
        }

        if (startDate == null)
            startDate = todayInIndia();
        if (endDate == null)
            endDate = startDate;

        List<AttendanceDTO> results = new ArrayList<>();
        for (Long uid : visibleUserIds) {
            User user = userRepository.findById(uid).orElse(null);
            if (user == null)
                continue;
            
            // Production Guard: Only fetch attendance for dates >= joiningDate
            LocalDate userJoinDate = user.getJoiningDate();
            LocalDate effectiveStart = (userJoinDate != null && userJoinDate.isAfter(startDate)) ? userJoinDate : startDate;

            for (LocalDate date = effectiveStart; !date.isAfter(endDate); date = date.plusDays(1)) {
                AttendanceDTO dto = fetchAttendanceForDate(user, date);
                if (dto != null) {
                    results.add(dto);
                }
            }
        }
        return results;
    }

    private AttendanceDTO fetchAttendanceForDate(User user, LocalDate date) {
        // Business Rule: Attendance is invalid before joining date
        if (user.getJoiningDate() != null && date.isBefore(user.getJoiningDate())) {
            return null; 
        }

        Optional<AttendanceSession> session = attendanceSessionRepository
                .findSessionsForDate(user.getId(), date.atStartOfDay(), date.atTime(23, 59, 59))
                .stream().findFirst();

        if (session.isPresent()) {
            return convertToDTO(session.get(), date);
        }

        Optional<AttendanceDaily> daily = attendanceDailyRepository
                .findByUserIdAndDate(user.getId(), date);

        if (daily.isPresent()) {
            return convertDailyToDTO(daily.get(), user, date);
        }

        return createAbsentDTO(user, date);
    }

    private void finalizeSession(AttendanceSession s) {
        LocalDateTime now = nowInIndia();

        // Final segment calculation before closing
        try {
            AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(s.getOffice().getId())
                    .orElseGet(() -> AttendancePolicy.builder().office(s.getOffice()).build());
            // Assume the user was in their last known state for radius check
            boolean lastInside = isInsideLastKnown(s);
            resolveAttendanceState(s, policy, now, lastInside);
        } catch (Exception e) {
            log.warn("Could not resolve final segment for session {}", s.getId());
        }

        s.setStatus(AttendanceStatus.PUNCHED_OUT);
        s.setCheckOutTime(now);
        attendanceSessionRepository.save(s);
        if (s.getCheckInTime() != null) {
            reconcileDailySummary(s.getUser().getId(), s.getCheckInTime().toLocalDate(), s.getOffice());
        }
    }

    private boolean isInsideLastKnown(AttendanceSession s) {
        if (s.getLastLat() == null)
            return true; // Fallback
        LocationRequestDTO lastLoc = new LocationRequestDTO();
        lastLoc.setLat(s.getLastLat());
        lastLoc.setLng(s.getLastLng());
        return isInsideOffice(s, lastLoc);
    }

    private void reconcileDailySummary(Long userId, LocalDate date, OfficeLocation office) {
        User user = userRepository.findById(userId).orElseThrow();
        
        if (user.getJoiningDate() != null && date.isBefore(user.getJoiningDate())) {
            return;
        }

        List<AttendanceSession> sessions = attendanceSessionRepository.findSessionsForDate(userId, date.atStartOfDay(),
                date.atTime(23, 59, 59));

        if (sessions.isEmpty()) {
            return; // don't create absent here
        }

        long totalWorkSecs = sessions.stream()
                .mapToLong(s -> s.getTotalWorkSeconds() != null ? s.getTotalWorkSeconds() : 0).sum();
        long totalBreakSecs = sessions.stream()
                .mapToLong(s -> s.getTotalBreakSeconds() != null ? s.getTotalBreakSeconds() : 0).sum();
        long totalOutsideSecs = sessions.stream()
                .mapToLong(s -> s.getUnauthorizedOutsideSeconds() != null ? s.getUnauthorizedOutsideSeconds() : 0).sum();

        AttendanceDaily daily = attendanceDailyRepository.findByUserIdAndDate(userId, date)
                .orElse(AttendanceDaily.builder().user(user).date(date).build());

        daily.setTotalWorkSeconds(totalWorkSecs);
        daily.setTotalWorkMinutes((int) (totalWorkSecs / 60));
        daily.setTotalBreakSeconds(totalBreakSecs);
        daily.setTotalBreakMinutes((int) (totalBreakSecs / 60));
        daily.setUnauthorizedOutsideSeconds(totalOutsideSecs);

        // Calculate dynamic thresholds based on the user's assigned shift
        long fullDaySecs = 28800; // Default 8 hours
        long halfDaySecs = 14400; // Default 4 hours
        
        if (user.getShift() != null) {
            fullDaySecs = user.getShift().getMinFullDayMinutes() * 60L;
            halfDaySecs = user.getShift().getMinHalfDayMinutes() * 60L;
        }

        if (totalWorkSecs >= fullDaySecs) {
            daily.setStatus("PRESENT");
        } else if (totalWorkSecs >= halfDaySecs) {
            daily.setStatus("HALF_DAY");
        } else {
            daily.setStatus("ABSENT");
        }
        attendanceDailyRepository.save(daily);
    }

    @Transactional
    public AttendanceDTO clockOut(Long userId) {
        AttendanceSession session = attendanceSessionRepository
                .findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(userId, ACTIVE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("No active session."));
        finalizeSession(session);
        return convertToDTO(session, session.getCheckInTime().toLocalDate());
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    @Transactional
    public void autoPunchOutIdleSessions() {
        LocalDateTime cutoff = nowInIndia().minusHours(2);
        List<AttendanceSession> inactiveSessions = attendanceSessionRepository.findInactiveSessions(ACTIVE_STATUSES,
                cutoff);
        for (AttendanceSession session : inactiveSessions) {
            try {
                log.info("Auto punch-out triggered for session id {}", session.getId());
                session.setAutoCheckout(true);
                finalizeSession(session);
            } catch (Exception e) {
                log.error("Failed to auto punch-out session {}", session.getId(), e);
            }
        }
    }

    @Transactional
    public AttendanceDTO startBreak(Long userId, String type) {
        AttendanceSession session = attendanceSessionRepository
                .findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(userId, ACTIVE_STATUSES)
                .orElseThrow(() -> new RuntimeException("No active session to start break."));
        session.setStatus(
                "LONG".equalsIgnoreCase(type) ? AttendanceStatus.ON_LONG_BREAK : AttendanceStatus.ON_SHORT_BREAK);
        session.setOutsideStartTime(nowInIndia());
        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Transactional
    public AttendanceDTO endBreak(Long userId) {
        AttendanceSession session = attendanceSessionRepository
                .findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(userId, ACTIVE_STATUSES)
                .orElseThrow(() -> new RuntimeException("No active session found."));
        session.setStatus(AttendanceStatus.WORKING);
        session.setOutsideStartTime(null);
        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceDTO> getCurrentStatus(Long userId) {
        List<AttendanceSession> sessions = attendanceSessionRepository.findLatestStatusNoLock(userId,
                ACTIVE_STATUSES,
                org.springframework.data.domain.PageRequest.of(0, 1));
        
        return sessions.stream().findFirst()
                .map(s -> convertToDTO(s, s.getCheckInTime() != null ? s.getCheckInTime().toLocalDate() : todayInIndia()));
    }

    @Transactional(readOnly = true)
    public List<AttendanceDTO> getMyLogs(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate end = todayInIndia();
        LocalDate start = end.minusDays(30);
        
        // Optimize: Don't look back further than joining date
        if (user.getJoiningDate() != null && user.getJoiningDate().isAfter(start)) {
            start = user.getJoiningDate();
        }
        
        return getDailySummaries(start, end, userId, userId);
    }

    private AttendanceDTO convertToDTO(AttendanceSession s, LocalDate date) {
        if (s == null)
            return null;
        AttendanceDTO dto = attendanceMapper.toDTO(s);
        if (dto != null) {
            dto.setDate(date != null ? date
                    : (s.getCheckInTime() != null ? s.getCheckInTime().toLocalDate() : todayInIndia()));
        }
        return dto;
    }

    private AttendanceDTO convertDailyToDTO(AttendanceDaily d, User u, LocalDate date) {
        AttendanceDTO dto = new AttendanceDTO();
        dto.setUserId(u.getId());
        dto.setUserName(u.getName());
        dto.setDate(date);
        dto.setStatus(d.getStatus());
        dto.setTotalWorkMinutes(d.getTotalWorkMinutes());
        dto.setTotalBreakMinutes(d.getTotalBreakMinutes());
        dto.setTotalIdleMinutes(d.getUnauthorizedOutsideSeconds() != null ? (int) (d.getUnauthorizedOutsideSeconds() / 60) : 0);
        return dto;
    }

    private AttendanceDTO createAbsentDTO(User u, LocalDate date) {
        AttendanceDTO dto = new AttendanceDTO();
        dto.setUserId(u.getId());
        dto.setUserName(u.getName());
        dto.setDate(date);
        dto.setStatus("ABSENT");
        return dto;
    }

    // Delegated to AttendancePolicyService in controllers, but kept here for
    // internal compatibility if needed
    public GlobalTarget getGlobalTarget() {
        return globalTargetRepository.findFirstByOrderByIdAsc().orElseGet(GlobalTarget::defaultTarget);
    }

    public GlobalTarget updateGlobalTarget(GlobalTarget updated) {
        GlobalTarget existing = getGlobalTarget();
        existing.setMonthlyLeadQuota(updated.getMonthlyLeadQuota());
        return globalTargetRepository.save(existing);
    }

    public List<AttendanceShift> getAllShifts() {
        return attendanceShiftRepository.findAll();
    }
}
