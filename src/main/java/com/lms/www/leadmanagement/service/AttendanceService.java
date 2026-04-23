package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.AttendancePolicyDTO;
import com.lms.www.leadmanagement.dto.LocationRequestDTO;
import com.lms.www.leadmanagement.dto.OfficeLocationDTO;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.entity.GlobalTarget;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.exception.SecurityViolationException;
import com.lms.www.leadmanagement.mapper.AttendanceMapper;
import com.lms.www.leadmanagement.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AttendanceService {

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired
    private AttendanceDailyRepository attendanceDailyRepository;
    @Autowired
    private OfficeLocationRepository officeLocationRepository;
    @Autowired
    private AttendancePolicyRepository attendancePolicyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AttendanceMapper attendanceMapper;
    @Autowired
    private GlobalTargetRepository globalTargetRepository;
    @Autowired
    private AttendanceAuditLogRepository auditLogRepository;

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final double MAX_SPEED_KMPH = 150.0;
    private static final double MAX_ACCURACY_METERS = 50.0;
    private static final double VELOCITY_JUMP_THRESHOLD_METERS = 500.0;
    private static final int HEARTBEAT_TOLERANCE_MINS = 3;
    private static final int PROGRESSIVE_GRACE_WARNING_MINS = 2;
    private static final int PROGRESSIVE_GRACE_TERMINATION_MINS = 5;
    private static final int MAX_TIME_INCREMENT_MINS = 2;

    // Default Policy Values
    private static final int DEFAULT_TRACKING_INTERVAL = 300;
    private static final int DEFAULT_GRACE_PERIOD = 2;
    private static final double DEFAULT_OFFICE_RADIUS = 200.0;
    private static final LocalTime DEFAULT_SHORT_BREAK_START = LocalTime.of(17, 0);
    private static final LocalTime DEFAULT_SHORT_BREAK_END = LocalTime.of(17, 10);
    private static final LocalTime DEFAULT_LONG_BREAK_START = LocalTime.of(13, 0);
    private static final LocalTime DEFAULT_LONG_BREAK_END = LocalTime.of(14, 0);

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

    private Optional<OfficeLocation> findNearestOffice(double lat, double lng) {
        return officeLocationRepository.findAll().stream()
                .min((o1, o2) -> Double.compare(
                        calculateDistance(lat, lng, o1.getLatitude(), o1.getLongitude()),
                        calculateDistance(lat, lng, o2.getLatitude(), o2.getLongitude())));
    }

    private String getIpHash(String ip) {
        return String.valueOf(ip != null ? ip.hashCode() : 0);
    }

    @Transactional
    public AttendanceDTO clockIn(LocationRequestDTO request, String ua, String ip) {
        Long userId = request.getUserId();

        if (request.isMockLocation()) {
            log.warn("Security violation for user {}: Mock location detected during Punch-In", userId);
            throw new SecurityViolationException("Security violation: Mock location detected. Deployment protocol terminated.");
        }

        attendanceSessionRepository.findByUserIdAndStatusIn(userId,
                List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK,
                        AttendanceStatus.AUTO_BREAK))
                .ifPresent(this::finalizeSession);

        OfficeLocation office = findNearestOffice(request.getLat(), request.getLng())
                .orElseThrow(
                        () -> new RuntimeException("No office locations defined. Admin must setup a branch first."));

        if (calculateDistance(request.getLat(), request.getLng(), office.getLatitude(), office.getLongitude()) > office
                .getRadius()) {
            throw new RuntimeException("Outside office zone. Move closer to " + office.getName());
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
                .userAgent(ua).ipHash(getIpHash(ip))
                .totalWorkMinutes(0).totalBreakMinutes(0).isAutoCheckout(false)
                .isLate(isLate)
                .build();

        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Transactional
    public AttendanceDTO trackLocation(LocationRequestDTO request, String ua, String ip) {
        Long userId = request.getUserId();
        
        if (request.isMockLocation()) {
            log.warn("Security violation for user {}: Mock location detected during Heartbeat", userId);
            throw new SecurityViolationException("Security violation: Mock location detected. Tracking session suspended.");
        }
        try {
            AttendanceSession session = attendanceSessionRepository.findActiveForUpdate(userId,
                    List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK,
                            AttendanceStatus.AUTO_BREAK))
                    .orElseThrow(
                            () -> new ResourceNotFoundException("No active session found. Please Clock In again."));

            if (session.getOffice() == null) {
                log.error("Fatal error: active attendance session #{} has no office node", session.getId());
                finalizeSession(session);
                throw new RuntimeException(
                        "Operational integrity violation: missing office link. Please Clock In again.");
            }

            AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(session.getOffice().getId())
                    .orElseGet(() -> AttendancePolicy.builder().office(session.getOffice()).build());

            LocalDateTime now = nowInIndia();

            validateSecurity(session, request, ip);
            performVelocityCheck(session, request, now);

            // Throttling
            long secondsSinceLast = Duration.between(session.getLastLocationTime(), now).getSeconds();
            int interval = policy.getTrackingIntervalSec() != null ? policy.getTrackingIntervalSec()
                    : DEFAULT_TRACKING_INTERVAL;
            if (secondsSinceLast < (interval / 2)) {
                return convertToDTO(session, session.getCheckInTime().toLocalDate());
            }

            // Stale Session Check
            int maxIdle = policy.getMaxIdleMinutes() != null ? policy.getMaxIdleMinutes() : 30;
            if (Duration.between(session.getLastSeenTime(), now).toMinutes() > maxIdle) {
                finalizeSession(session);
                throw new RuntimeException("Session timed out. Please Clock In again.");
            }

            handleGeofenceAndWorkStatus(session, request, policy, now);

            session.setLastLat(request.getLat());
            session.setLastLng(request.getLng());
            session.setLastAccuracy(request.getAccuracy() != null ? request.getAccuracy() : session.getLastAccuracy());
            session.setLastLocationTime(now);

            return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
        } catch (PessimisticLockingFailureException e) {
            throw new RuntimeException("System busy. Try again.");
        }
    }

    private void validateSecurity(AttendanceSession session, LocationRequestDTO request, String ip) {
        // If device ID is provided by client, it must match. If null, we skip this
        // check for web-based tracking resilience.
        if (request.getDeviceId() != null && !request.getDeviceId().equals(session.getDeviceId())) {
            throw new RuntimeException("Security violation: Device mismatch.");
        }

        // if (!session.getIpHash().equals(getIpHash(ip))) {
        //     throw new RuntimeException("Security violation: Network change detected.");
        // }
    }

    private void performVelocityCheck(AttendanceSession session, LocationRequestDTO request, LocalDateTime now) {
        // 1. Accuracy Filter
        Double accuracy = request.getAccuracy();
        if (accuracy != null && accuracy > MAX_ACCURACY_METERS) {
            log.warn("Low accuracy GPS for user {}: {}m (Limit: {}m)", session.getUser().getId(), accuracy,
                    MAX_ACCURACY_METERS);
            throw new RuntimeException("Inaccurate location data. Please move to an open area.");
        }

        if (session.getLastLat() != null && session.getLastLng() != null && session.getLastLocationTime() != null) {
            double metersMoved = calculateDistance(session.getLastLat(), session.getLastLng(), request.getLat(),
                    request.getLng());
            long secondsElapsed = Duration.between(session.getLastLocationTime(), now).toSeconds();

            if (secondsElapsed > 0) {
                // 2. Velocity Check
                double kmph = (metersMoved / 1000.0) / (secondsElapsed / 3600.0);
                if (kmph > MAX_SPEED_KMPH) {
                    log.warn("Suspicious velocity for user {}: {} km/h (Limit: {} km/h)", session.getUser().getId(),
                            kmph, MAX_SPEED_KMPH);
                    throw new RuntimeException("Suspicious activity detected. Location rejected.");
                }

                // 3. Jump Detection (Teleportation Check)
                if (secondsElapsed < 10 && metersMoved > VELOCITY_JUMP_THRESHOLD_METERS) {
                    log.warn("Sudden location jump for user {}: {}m in {}s", session.getUser().getId(), metersMoved,
                            secondsElapsed);
                    throw new RuntimeException("System detected a sudden location jump. Please disable Mock GPS.");
                }
            }
        }
    }

    private void handleGeofenceAndWorkStatus(AttendanceSession session, LocationRequestDTO request,
            AttendancePolicy policy, LocalDateTime now) {
        boolean isInside = false;
        User user = session.getUser();

        if (user.getAssignedOffice() != null) {
            double dist = calculateDistance(request.getLat(), request.getLng(),
                    user.getAssignedOffice().getLatitude(), user.getAssignedOffice().getLongitude());
            if (dist <= user.getAssignedOffice().getRadius()) {
                session.setOffice(user.getAssignedOffice());
                isInside = true;
            }
        }

        if (!isInside) {
            Optional<OfficeLocation> otherOffice = officeLocationRepository.findAll().stream()
                    .filter(o -> calculateDistance(request.getLat(), request.getLng(), o.getLatitude(),
                            o.getLongitude()) <= o.getRadius())
                    .findFirst();
            if (otherOffice.isPresent()) {
                session.setOffice(otherOffice.get());
                isInside = true;
            }
        }

        // 3. Resolve State Machine with sub-second precision (V3)
        resolveAttendanceState(session, policy, now, isInside);
    }

    private void resolveAttendanceState(AttendanceSession session, AttendancePolicy policy, LocalDateTime now,
            boolean currentlyInside) {
        AttendanceStatus oldStatus = session.getStatus();
        LocalDateTime lastPing = session.getLastSeenTime() != null ? session.getLastSeenTime() : session.getCheckInTime();
        
        // 1. Determine "Was Inside" from database state
        boolean wasInsideAtStart = (session.getOutsideStartTime() == null);
        
        // 2. Build Timeline Boundaries for this interval [lastPing, now]
        TreeSet<LocalDateTime> bounds = new TreeSet<>();
        bounds.add(lastPing);
        bounds.add(now);

        // Add Break Boundaries
        addBreakBounds(bounds, lastPing.toLocalDate(), policy);

        // Add Grace Expiry Boundary (if user was already outside)
        if (!wasInsideAtStart) {
            int graceSecs = (policy.getGracePeriodMinutes() != null ? policy.getGracePeriodMinutes() : DEFAULT_GRACE_PERIOD) * 60;
            LocalDateTime graceExpiry = session.getOutsideStartTime().plusSeconds(graceSecs);
            if (graceExpiry.isAfter(lastPing) && graceExpiry.isBefore(now)) {
                bounds.add(graceExpiry);
            }
        }

        // 3. Process each segment accurately
        List<LocalDateTime> timeline = new ArrayList<>(bounds);
        for (int i = 0; i < timeline.size() - 1; i++) {
            LocalDateTime start = timeline.get(i);
            LocalDateTime end = timeline.get(i + 1);
            long segmentSecs = Duration.between(start, end).toSeconds();
            if (segmentSecs <= 0) continue;

            LocalDateTime mid = start.plusSeconds(segmentSecs / 2);
            LocalTime midTime = mid.toLocalTime();

            // Priority 1: Break Time (Overrides Location)
            if (isTimeInBreak(midTime, policy)) {
                session.setTotalBreakSeconds(session.getTotalBreakSeconds() + segmentSecs);
            } 
            // Priority 2: Work Time (User was Inside)
            else if (wasInsideAtStart) {
                session.setTotalWorkSeconds(session.getTotalWorkSeconds() + segmentSecs);
            }
            // Priority 3: Idle Time (User was Outside AND past grace boundary)
            else {
                LocalDateTime outsideSince = session.getOutsideStartTime();
                long totalOutsideSecsAtMid = Duration.between(outsideSince, mid).toSeconds();
                int graceLimitSecs = (policy.getGracePeriodMinutes() != null ? policy.getGracePeriodMinutes() : DEFAULT_GRACE_PERIOD) * 60;

                if (totalOutsideSecsAtMid > graceLimitSecs) {
                    session.setUnauthorizedOutsideSeconds(session.getUnauthorizedOutsideSeconds() + segmentSecs);
                }
            }
        }

        // 4. Update state for NEXT interval
        if (currentlyInside) {
            session.setOutsideStartTime(null);
        } else if (wasInsideAtStart) {
            session.setOutsideStartTime(now);
            session.setOutsideCount((session.getOutsideCount() != null ? session.getOutsideCount() : 0) + 1);
        }

        // 5. Visual Status & Compatibility Update (Seconds -> Minutes)
        session.setTotalWorkMinutes((int) (session.getTotalWorkSeconds() / 60));
        session.setTotalBreakMinutes((int) (session.getTotalBreakSeconds() / 60));
        session.setUnauthorizedOutsideMinutes((int) (session.getUnauthorizedOutsideSeconds() / 60));

        updateAttendanceStatusV3(session, policy, now, currentlyInside, oldStatus);

        session.setLastSeenTime(now);

        if (oldStatus != session.getStatus()) {
            logAudit(session, oldStatus.name(), session.getStatus().name(), "V3 Precision Machine: Boundary Transition");
        }
    }

    private void addBreakBounds(TreeSet<LocalDateTime> bounds, LocalDate date, AttendancePolicy policy) {
        LocalTime lbStart = policy.getLongBreakStartTime() != null ? policy.getLongBreakStartTime() : DEFAULT_LONG_BREAK_START;
        LocalTime lbEnd = policy.getLongBreakEndTime() != null ? policy.getLongBreakEndTime() : DEFAULT_LONG_BREAK_END;
        LocalTime sbStart = policy.getShortBreakStartTime() != null ? policy.getShortBreakStartTime() : DEFAULT_SHORT_BREAK_START;
        LocalTime sbEnd = policy.getShortBreakEndTime() != null ? policy.getShortBreakEndTime() : DEFAULT_SHORT_BREAK_END;

        bounds.add(date.atTime(lbStart));
        bounds.add(date.atTime(lbEnd));
        bounds.add(date.atTime(sbStart));
        bounds.add(date.atTime(sbEnd));
    }

    private boolean isTimeInBreak(LocalTime time, AttendancePolicy policy) {
        return isWithinBreak(time, policy.getLongBreakStartTime(), policy.getLongBreakEndTime(), DEFAULT_LONG_BREAK_START, DEFAULT_LONG_BREAK_END) ||
               isWithinBreak(time, policy.getShortBreakStartTime(), policy.getShortBreakEndTime(), DEFAULT_SHORT_BREAK_START, DEFAULT_SHORT_BREAK_END);
    }

    private void updateAttendanceStatusV3(AttendanceSession session, AttendancePolicy policy, LocalDateTime now, boolean inside, AttendanceStatus old) {
        LocalTime currentTime = now.toLocalTime();
        if (isTimeInBreak(currentTime, policy)) {
            boolean isLong = isWithinBreak(currentTime, policy.getLongBreakStartTime(), policy.getLongBreakEndTime(), DEFAULT_LONG_BREAK_START, DEFAULT_LONG_BREAK_END);
            session.setStatus(isLong ? AttendanceStatus.ON_LONG_BREAK : AttendanceStatus.ON_SHORT_BREAK);
        } else if (inside) {
            session.setStatus(AttendanceStatus.WORKING);
        } else {
            LocalDateTime since = session.getOutsideStartTime();
            int graceSecs = (policy.getGracePeriodMinutes() != null ? policy.getGracePeriodMinutes() : DEFAULT_GRACE_PERIOD) * 60;
            if (since != null && Duration.between(since, now).toSeconds() > graceSecs) {
                session.setStatus(AttendanceStatus.OUTSIDE_UNAUTHORIZED);
                if (old != AttendanceStatus.OUTSIDE_UNAUTHORIZED) {
                    session.setBreakViolations((session.getBreakViolations() != null ? session.getBreakViolations() : 0) + 1);
                }
            } else {
                session.setStatus(old);
            }
        }
    }


    private void logAudit(AttendanceSession session, String fromStatus, String toStatus, String reason) {
        AttendanceAuditLog logEntry = AttendanceAuditLog.builder()
                .user(session.getUser())
                .session(session)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .latitude(session.getLastLat())
                .longitude(session.getLastLng())
                .accuracy(session.getLastAccuracy())
                .reason(reason)
                .build();
        auditLogRepository.save(logEntry);
    }

    private boolean isWithinBreak(LocalTime current, LocalTime policyStart, LocalTime policyEnd, LocalTime defaultStart,
            LocalTime defaultEnd) {
        LocalTime start = policyStart != null ? policyStart : defaultStart;
        LocalTime end = policyEnd != null ? policyEnd : defaultEnd;
        return !current.isBefore(start) && !current.isAfter(end);
    }

    @Transactional
    public List<OfficeLocationDTO> getAllOffices() {
        return officeLocationRepository.findAll().stream()
                .map(attendanceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public OfficeLocation createOffice(OfficeLocation office) {
        return officeLocationRepository.save(office);
    }

    @Transactional
    public void deleteOffice(Long id) {
        if (!officeLocationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Office node not found");
        }

        // Critical: Check if any attendance history exists for this node
        if (attendanceSessionRepository.existsByOfficeId(id)) {
            throw new IllegalStateException(
                    "Operational integrity violation: Identified active or historical attendance logs linked to this node. Archive logs before decommissioning the branch.");
        }

        // Automated cleanup: Remove linked policies if no history exists
        attendancePolicyRepository.deleteByOfficeId(id);

        officeLocationRepository.deleteById(id);
    }

    @Transactional
    public List<AttendancePolicyDTO> getAllPolicies() {
        return attendancePolicyRepository.findAll().stream()
                .map(attendanceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendancePolicy createPolicy(AttendancePolicyDTO dto) {
        OfficeLocation office = officeLocationRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        AttendancePolicy policy = AttendancePolicy.builder()
                .office(office)
                .shortBreakStartTime(
                        dto.getShortBreakStartTime() != null ? LocalTime.parse(dto.getShortBreakStartTime())
                                : DEFAULT_SHORT_BREAK_START)
                .shortBreakEndTime(dto.getShortBreakEndTime() != null ? LocalTime.parse(dto.getShortBreakEndTime())
                        : DEFAULT_SHORT_BREAK_END)
                .longBreakStartTime(dto.getLongBreakStartTime() != null ? LocalTime.parse(dto.getLongBreakStartTime())
                        : DEFAULT_LONG_BREAK_START)
                .longBreakEndTime(dto.getLongBreakEndTime() != null ? LocalTime.parse(dto.getLongBreakEndTime())
                        : DEFAULT_LONG_BREAK_END)
                .gracePeriodMinutes(
                        dto.getGracePeriodMinutes() != null ? dto.getGracePeriodMinutes() : DEFAULT_GRACE_PERIOD)
                .trackingIntervalSec(
                        dto.getTrackingIntervalSec() != null ? dto.getTrackingIntervalSec() : DEFAULT_TRACKING_INTERVAL)
                .maxAccuracyMeters(dto.getMaxAccuracyMeters())
                .minimumWorkMinutes(dto.getMinimumWorkMinutes())
                .maxIdleMinutes(dto.getMaxIdleMinutes())
                .build();

        return attendancePolicyRepository.save(policy);
    }

    @Transactional
    public AttendancePolicy updatePolicy(Long id, AttendancePolicyDTO dto) {
        AttendancePolicy policy = attendancePolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));

        if (dto.getShortBreakStartTime() != null)
            policy.setShortBreakStartTime(LocalTime.parse(dto.getShortBreakStartTime()));
        if (dto.getShortBreakEndTime() != null)
            policy.setShortBreakEndTime(LocalTime.parse(dto.getShortBreakEndTime()));
        if (dto.getLongBreakStartTime() != null)
            policy.setLongBreakStartTime(LocalTime.parse(dto.getLongBreakStartTime()));
        if (dto.getLongBreakEndTime() != null)
            policy.setLongBreakEndTime(LocalTime.parse(dto.getLongBreakEndTime()));
        if (dto.getGracePeriodMinutes() != null)
            policy.setGracePeriodMinutes(dto.getGracePeriodMinutes());
        if (dto.getTrackingIntervalSec() != null)
            policy.setTrackingIntervalSec(dto.getTrackingIntervalSec());
        if (dto.getMaxAccuracyMeters() != null)
            policy.setMaxAccuracyMeters(dto.getMaxAccuracyMeters());
        if (dto.getMinimumWorkMinutes() != null)
            policy.setMinimumWorkMinutes(dto.getMinimumWorkMinutes());
        if (dto.getMaxIdleMinutes() != null)
            policy.setMaxIdleMinutes(dto.getMaxIdleMinutes());

        return attendancePolicyRepository.save(policy);
    }

    @Transactional
    public void deletePolicy(Long id) {
        if (!attendancePolicyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Policy not found");
        }
        attendancePolicyRepository.deleteById(id);
    }

    @Transactional
    public List<AttendanceShift> getAllShifts() {
        return attendanceShiftRepository.findAll();
    }

    @Autowired
    private AttendanceShiftRepository attendanceShiftRepository;

    @Transactional
    public AttendanceShift createShift(AttendanceShift shift) {
        return attendanceShiftRepository.save(shift);
    }

    @Transactional
    public AttendanceShift updateShift(Long id, AttendanceShift updatedShift) {
        if (id == null) {
            throw new IllegalArgumentException("Shift ID must not be null");
        }
        AttendanceShift shift = attendanceShiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        shift.setName(updatedShift.getName());
        shift.setStartTime(updatedShift.getStartTime());
        shift.setEndTime(updatedShift.getEndTime());
        shift.setGraceMinutes(updatedShift.getGraceMinutes());
        shift.setMinHalfDayMinutes(updatedShift.getMinHalfDayMinutes());
        shift.setMinFullDayMinutes(updatedShift.getMinFullDayMinutes());
        shift.setOffice(updatedShift.getOffice());

        return attendanceShiftRepository.save(shift);
    }

    @Transactional
    public void deleteShift(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Shift ID must not be null");
        }
        if (!attendanceShiftRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shift not found");
        }
        attendanceShiftRepository.deleteById(id);
    }

    @Transactional
    public GlobalTarget getGlobalTarget() {
        return globalTargetRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> globalTargetRepository.save(GlobalTarget.defaultTarget()));
    }

    @Transactional
    public GlobalTarget updateGlobalTarget(GlobalTarget updated) {
        GlobalTarget existing = getGlobalTarget();
        existing.setMonthlyLeadQuota(updated.getMonthlyLeadQuota());
        existing.setTargetConversionRate(updated.getTargetConversionRate());
        existing.setTargetRetentionRate(updated.getTargetRetentionRate());
        existing.setMonthlyRevenueGoal(updated.getMonthlyRevenueGoal());
        existing.setActiveMemberThreshold(updated.getActiveMemberThreshold());
        existing.setBaseIncentiveAmount(updated.getBaseIncentiveAmount());
        existing.setTargetIncentiveAmount(updated.getTargetIncentiveAmount());
        return globalTargetRepository.save(existing);
    }

    @Transactional
    public OfficeLocation updateOffice(Long id, OfficeLocation updated) {
        OfficeLocation office = officeLocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));
        office.setName(updated.getName());
        office.setLatitude(updated.getLatitude());
        office.setLongitude(updated.getLongitude());
        office.setRadius(updated.getRadius());
        return officeLocationRepository.save(office);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDTO> getDailySummaries(LocalDate startDate, LocalDate endDate, Long targetUserId,
            Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        String role = requester.getRole().getName();

        List<User> visibleUsers = new ArrayList<>();
        if ("ADMIN".equals(role)) {
            if (targetUserId != null) {
                User target = userRepository.findById(targetUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));
                visibleUsers = List.of(target);
            } else {
                // Admin sees all eligible staff
                visibleUsers = userRepository.findAll().stream()
                        .filter(u -> !"ADMIN".equals(u.getRole().getName()))
                        .collect(Collectors.toList());
            }
        } else {
            // Manager/TL hierarchy restriction
            List<User> subordinates = new ArrayList<>();
            subordinates.add(requester);
            collectSubordinates(requester, subordinates);

            if (targetUserId != null) {
                if (subordinates.stream().anyMatch(u -> u.getId().equals(targetUserId))) {
                    User target = userRepository.findById(targetUserId)
                            .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));
                    visibleUsers = List.of(target);
                } else {
                    return Collections.emptyList(); // Unauthorized target
                }
            } else {
                visibleUsers = subordinates;
            }
        }

        // If no date provided, default to today
        if (startDate == null) startDate = todayInIndia();
        if (endDate == null) endDate = startDate;

        List<AttendanceDTO> results = new ArrayList<>();

        for (User user : visibleUsers) {
            // For each day in range
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                // Priority 1: Check for active session today
                Optional<AttendanceSession> activeSession = attendanceSessionRepository.findSessionsForDate(
                    user.getId(), 
                    date.atStartOfDay(), 
                    date.atTime(23, 59, 59)
                ).stream().findFirst();

                if (activeSession.isPresent()) {
                    results.add(convertToDTO(activeSession.get(), date));
                } else {
                    // Priority 2: Check for historic daily record
                    Optional<AttendanceDaily> daily = attendanceDailyRepository.findByUserIdAndDate(user.getId(), date);
                    if (daily.isPresent()) {
                        AttendanceDaily log = daily.get();
                        AttendanceStatus parsedStatus = AttendanceStatus.PUNCHED_OUT;
                        if (log.getStatus() != null) {
                            try { parsedStatus = AttendanceStatus.valueOf(log.getStatus()); }
                            catch (Exception e) { parsedStatus = AttendanceStatus.PUNCHED_OUT; }
                        }
                        AttendanceSession dummy = AttendanceSession.builder()
                                .user(user)
                                .checkInTime(log.getDate().atStartOfDay())
                                .status(parsedStatus)
                                .totalWorkMinutes(log.getTotalWorkMinutes())
                                .totalBreakMinutes(log.getTotalBreakMinutes())
                                .outsideCount(log.getOutsideCount())
                                .build();
                        results.add(convertToDTO(dummy, date));
                    } else {
                        // Priority 3: No record found? Show as ABSENT (only for past/current days)
                        if (!date.isAfter(todayInIndia())) {
                            AttendanceSession dummy = AttendanceSession.builder()
                                    .user(user)
                                    .status(AttendanceStatus.OUT)
                                    .totalWorkMinutes(0)
                                    .build();
                            AttendanceDTO dto = convertToDTO(dummy, date);
                            dto.setStatus("ABSENT");
                            results.add(dto);
                        }
                    }
                }
            }
        }
        return results;
    }

    private void collectSubordinates(User user, List<User> collector) {
        List<User> subs = new ArrayList<>();
        subs.addAll(userRepository.findByManager(user));
        subs.addAll(userRepository.findBySupervisor(user));

        for (User sub : subs) {
            if (!collector.contains(sub)) {
                collector.add(sub);
                collectSubordinates(sub, collector);
            }
        }
    }

    private void finalizeSession(AttendanceSession s) {
        LocalDateTime now = nowInIndia();

        // Final work/break duration update before closing
        long finalMins = Duration.between(s.getLastSeenTime() != null ? s.getLastSeenTime() : s.getCheckInTime(), now)
                .toMinutes();
        if (finalMins > 0) {
            if (s.getStatus() == AttendanceStatus.WORKING) {
                s.setTotalWorkMinutes(s.getTotalWorkMinutes() + (int) finalMins);
            } else if (s.getStatus() == AttendanceStatus.ON_SHORT_BREAK ||
                    s.getStatus() == AttendanceStatus.ON_LONG_BREAK ||
                    s.getStatus() == AttendanceStatus.AUTO_BREAK) {
                s.setTotalBreakMinutes(s.getTotalBreakMinutes() + (int) finalMins);
            }
        }

        s.setStatus(AttendanceStatus.PUNCHED_OUT);
        s.setCheckOutTime(now);
        s.setAutoCheckout(true);
        s.setLastSeenTime(now);
        attendanceSessionRepository.save(s);
        reconcileDailySummary(s.getUser().getId(), todayInIndia(), s.getOffice());
    }

    private void reconcileDailySummary(Long userId, LocalDate date, OfficeLocation office) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        AttendanceShift shift = user.getShift();
        
        List<AttendanceSession> sessions = attendanceSessionRepository.findSessionsForDate(userId, date.atStartOfDay(),
                date.atTime(23, 59, 59));

        long totalWorkSecs = sessions.stream().mapToLong(s -> s.getTotalWorkSeconds() != null ? s.getTotalWorkSeconds() : (s.getTotalWorkMinutes() * 60L)).sum();
        long totalBreakSecs = sessions.stream().mapToLong(s -> s.getTotalBreakSeconds() != null ? s.getTotalBreakSeconds() : (s.getTotalBreakMinutes() * 60L)).sum();
        long totalOutsideSecs = sessions.stream().mapToLong(s -> s.getUnauthorizedOutsideSeconds() != null ? s.getUnauthorizedOutsideSeconds() : (s.getUnauthorizedOutsideMinutes() * 60L)).sum();
        int totalOutsideCount = sessions.stream().mapToInt(s -> s.getOutsideCount() != null ? s.getOutsideCount() : 0).sum();

        int totalWorkMins = (int) (totalWorkSecs / 60);
        int totalBreakMins = (int) (totalBreakSecs / 60);

        AttendanceDaily daily = attendanceDailyRepository.findByUserIdAndDate(userId, date)
                .orElse(AttendanceDaily.builder().user(user).date(date).build());

        daily.setTotalWorkSeconds(totalWorkSecs);
        daily.setTotalBreakSeconds(totalBreakSecs);
        daily.setUnauthorizedOutsideSeconds(totalOutsideSecs);
        
        daily.setTotalWorkMinutes(totalWorkMins);
        daily.setTotalBreakMinutes(totalBreakMins);
        daily.setOutsideCount(totalOutsideCount);

        if (shift == null) {
            AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(office.getId())
                    .orElseGet(() -> AttendancePolicy.builder().office(office).build());
            daily.setStatus(totalWorkMins >= (policy.getMinimumWorkMinutes() != null ? policy.getMinimumWorkMinutes() : 480)
                    ? "PRESENT" : "ABSENT");
        } else {
            if (!sessions.isEmpty()) {
                LocalTime firstPunch = sessions.get(0).getCheckInTime().toLocalTime();
                daily.setLate(firstPunch.isAfter(shift.getStartTime().plusMinutes(shift.getGraceMinutes())));
                
                AttendanceSession lastSession = sessions.get(sessions.size() - 1);
                if (lastSession.getCheckOutTime() != null) {
                    daily.setEarlyExit(lastSession.getCheckOutTime().toLocalTime().isBefore(shift.getEndTime()));
                }
            }

            if (totalWorkMins >= shift.getMinFullDayMinutes()) {
                daily.setStatus("PRESENT");
            } else if (totalWorkMins >= shift.getMinHalfDayMinutes()) {
                daily.setStatus("HALF_DAY");
            } else {
                daily.setStatus("ABSENT");
            }

            long shiftSecs = Duration.between(shift.getStartTime(), shift.getEndTime()).toSeconds();
            daily.setOvertimeMinutes(Math.max(0, (int) ((totalWorkSecs - shiftSecs) / 60)));
        }
        
        attendanceDailyRepository.save(daily);
    }

    @Transactional
    public AttendanceDTO clockOut(Long userId) {
        AttendanceSession session = attendanceSessionRepository.findActiveForUpdate(userId,
                List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK,
                        AttendanceStatus.AUTO_BREAK))
                .orElseThrow(() -> new ResourceNotFoundException("No active session to clock out."));

        finalizeSession(session);
        session.setAutoCheckout(false);
        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Transactional
    public AttendanceDTO startBreak(Long userId, String type) {
        AttendanceSession session = attendanceSessionRepository
                .findActiveForUpdate(userId, List.of(AttendanceStatus.WORKING))
                .orElseThrow(() -> new RuntimeException("Must be in WORKING status to start a break."));

        AttendanceStatus breakStatus = "LONG".equalsIgnoreCase(type) ? AttendanceStatus.ON_LONG_BREAK
                : AttendanceStatus.ON_SHORT_BREAK;

        session.setStatus(breakStatus);
        session.setOutsideStartTime(nowInIndia());
        session.setLastSeenTime(nowInIndia());

        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Transactional
    public AttendanceDTO endBreak(Long userId) {
        AttendanceSession session = attendanceSessionRepository.findActiveForUpdate(userId,
                List.of(AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.AUTO_BREAK))
                .orElseThrow(() -> new ResourceNotFoundException("No active break found to end."));

        LocalDateTime now = nowInIndia();
        long deltaMins = Duration.between(
                session.getOutsideStartTime() != null ? session.getOutsideStartTime() : session.getLastSeenTime(), now)
                .toMinutes();

        session.setTotalBreakMinutes(session.getTotalBreakMinutes() + (int) deltaMins);
        session.setStatus(AttendanceStatus.WORKING);
        session.setOutsideStartTime(null);
        session.setLastSeenTime(now);

        return convertToDTO(attendanceSessionRepository.save(session), session.getCheckInTime().toLocalDate());
    }

    @Scheduled(fixedRate = 600000)
    @Transactional
    public void cleanupStaleSessions() {
        LocalDateTime now = nowInIndia();
        attendanceSessionRepository
                .findAllByStatusIn(List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK,
                        AttendanceStatus.ON_LONG_BREAK, AttendanceStatus.AUTO_BREAK))
                .stream()
                .forEach(s -> {
                    AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(s.getOffice().getId())
                            .orElseGet(() -> AttendancePolicy.builder().office(s.getOffice()).build());
                    int maxIdle = policy.getMaxIdleMinutes() != null ? policy.getMaxIdleMinutes() : 30;
                    if (Duration.between(s.getLastSeenTime(), now).toMinutes() > maxIdle) {
                        finalizeSession(s);
                    }
                });
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markDailyAbsenteeism() {
        LocalDate yesterday = todayInIndia().minusDays(1);
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if ("ADMIN".equalsIgnoreCase(user.getRole().getName()))
                continue;

            Optional<AttendanceDaily> dailyOpt = attendanceDailyRepository.findByUserIdAndDate(user.getId(), yesterday);
            if (dailyOpt.isEmpty()) {
                attendanceDailyRepository.save(java.util.Objects.requireNonNull(AttendanceDaily.builder()
                        .user(user).date(yesterday).status("ABSENT")
                        .totalWorkMinutes(0).totalBreakMinutes(0).outsideCount(0)
                        .build()));
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceDTO> getCurrentStatus(Long userId) {
        return attendanceSessionRepository.findByUserIdAndStatusIn(userId,
                List.of(AttendanceStatus.WORKING, AttendanceStatus.ON_SHORT_BREAK, AttendanceStatus.ON_LONG_BREAK,
                        AttendanceStatus.AUTO_BREAK))
                .map(s -> convertToDTO(s, s.getCheckInTime().toLocalDate()));
    }

    @Transactional(readOnly = true)
    public List<AttendanceDTO> getMyLogs(Long userId) {
        return attendanceSessionRepository.findByUserIdOrderByCheckInTimeDesc(userId).stream()
                .map(s -> convertToDTO(s, s.getCheckInTime().toLocalDate()))
                .collect(Collectors.toList());
    }

    private AttendanceDTO convertToDTO(AttendanceSession s, LocalDate date) {
        if (s == null)
            return null;

        OfficeLocation office = s.getOffice();
        AttendancePolicy policy = office != null
                ? attendancePolicyRepository.findByOfficeId(office.getId()).orElse(null)
                : null;

        if (date == null) {
            date = s.getCheckInTime() != null ? s.getCheckInTime().toLocalDate() : todayInIndia();
        }

        Long userId = (s.getUser() != null) ? s.getUser().getId() : null;
        int dayWork = 0;
        int dayBreak = 0;
        if (userId != null) {
            AttendanceDaily daily = attendanceDailyRepository.findByUserIdAndDate(userId, date).orElse(null);
            
            boolean isFinalized = s.getStatus() != null && s.getStatus() == AttendanceStatus.PUNCHED_OUT;
            int priorWork = (daily != null && daily.getTotalWorkMinutes() != null) ? daily.getTotalWorkMinutes() : 0;
            int priorBreak = (daily != null && daily.getTotalBreakMinutes() != null) ? daily.getTotalBreakMinutes() : 0;
            
            int activeWork = (!isFinalized && s.getTotalWorkMinutes() != null) ? s.getTotalWorkMinutes() : 
                             (daily == null && s.getTotalWorkMinutes() != null ? s.getTotalWorkMinutes() : 0);
            int activeBreak = (!isFinalized && s.getTotalBreakMinutes() != null) ? s.getTotalBreakMinutes() : 
                              (daily == null && s.getTotalBreakMinutes() != null ? s.getTotalBreakMinutes() : 0);

            dayWork = priorWork + activeWork;
            dayBreak = priorBreak + activeBreak;
            // Centralized Status Logic: Favor specific daily status if reconciled,
            // otherwise calculate from session
            String status = s.getStatus() != null ? s.getStatus().name() : "ABSENT";
            if (userId != null) {
                daily = attendanceDailyRepository.findByUserIdAndDate(userId, date).orElse(null);
                if (daily != null && daily.getStatus() != null) {
                    status = daily.getStatus();
                } else {
                    // Fallback: Dynamic calculation if daily record doesn't exist yet
                    User user = s.getUser();
                    AttendanceShift shift = (user != null) ? user.getShift() : null;
                    int minFull = (shift != null) ? shift.getMinFullDayMinutes() : 480;
                    int minHalf = (shift != null) ? shift.getMinHalfDayMinutes() : 240;

                    if (dayWork >= minFull)
                        status = "PRESENT";
                    else if (dayWork >= minHalf)
                        status = "HALF_DAY";
                    else
                        status = "ABSENT";
                }
            }
        }
        String dayHours = String.format("%dh %dm", dayWork / 60, dayWork % 60);
        AttendanceDTO dto = attendanceMapper.toDTO(s, policy, dayWork, dayHours, date);
        if (s.getStatus() != null) {
            dto.setStatus(s.getStatus().name());
        }
        return dto;
    }

    private long getOverlapSeconds(LocalDateTime start, LocalDateTime end, LocalTime windowStart, LocalTime windowEnd) {
        if (windowStart == null || windowEnd == null)
            return 0;

        LocalDateTime wStart = start.toLocalDate().atTime(windowStart);
        LocalDateTime wEnd = start.toLocalDate().atTime(windowEnd);

        // Handle case where break might end after midnight
        if (wEnd.isBefore(wStart))
            wEnd = wEnd.plusDays(1);

        LocalDateTime iStart = start.isAfter(wStart) ? start : wStart;
        LocalDateTime iEnd = end.isBefore(wEnd) ? end : wEnd;

        return iStart.isBefore(iEnd) ? Duration.between(iStart, iEnd).toSeconds() : 0;
    }

    /**
     * Automated Nightly Sweeper Job
     * Runs natively at 23:55 (11:55 PM) daily to systematically log ABSENT records 
     * for any registered active user who did not punch in.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 55 23 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void markAbsentUsersForToday() {
        log.info(">>> [ATTENDANCE SWEEPER] Initializing End-of-Day Absence Sweep...");
        LocalDate today = todayInIndia();
        List<User> activeUsers = userRepository.findAll();
        long unpunchedCount = 0;

        for (User user : activeUsers) {
            // Ignore system admin roles if necessary (Optional strategy)
            if (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getName())) {
                continue;
            }

            Boolean exists = attendanceDailyRepository.existsByUserIdAndDate(user.getId(), today);
            if (!exists) {
                // Generate the permanent Absent Tracker Row
                AttendanceDaily absentLog = AttendanceDaily.builder()
                        .user(user)
                        .date(today)
                        .totalWorkMinutes(0)
                        .totalBreakMinutes(0)
                        .totalWorkSeconds(0L)
                        .totalBreakSeconds(0L)
                        .unauthorizedOutsideSeconds(0L)
                        .outsideCount(0)
                        .status("ABSENT")
                        .isLate(false)
                        .isEarlyExit(false)
                        .overtimeMinutes(0)
                        .build();

                attendanceDailyRepository.save(absentLog);
                unpunchedCount++;
            }
        }
        log.info(">>> [ATTENDANCE SWEEPER] Complete. Generated {} ABSENT records for date: {}", unpunchedCount, today);
    }
}
