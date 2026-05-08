package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.AttendancePolicyDTO;
import com.lms.www.leadmanagement.dto.OfficeLocationDTO;
import com.lms.www.leadmanagement.entity.AttendancePolicy;
import com.lms.www.leadmanagement.entity.AttendanceShift;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.mapper.AttendanceMapper;
import com.lms.www.leadmanagement.repository.AttendancePolicyRepository;
import com.lms.www.leadmanagement.repository.AttendanceSessionRepository;
import com.lms.www.leadmanagement.repository.AttendanceShiftRepository;
import com.lms.www.leadmanagement.repository.OfficeLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendancePolicyService {

    private final OfficeLocationRepository officeLocationRepository;
    private final AttendancePolicyRepository attendancePolicyRepository;
    private final AttendanceShiftRepository attendanceShiftRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceMapper attendanceMapper;

    // Defaults
    private static final LocalTime DEFAULT_SHORT_BREAK_START = LocalTime.of(17, 0);
    private static final LocalTime DEFAULT_SHORT_BREAK_END = LocalTime.of(17, 10);
    private static final LocalTime DEFAULT_LONG_BREAK_START = LocalTime.of(13, 0);
    private static final LocalTime DEFAULT_LONG_BREAK_END = LocalTime.of(14, 0);
    private static final int DEFAULT_GRACE_PERIOD = 2;
    private static final int DEFAULT_TRACKING_INTERVAL = 300;

    @Transactional(readOnly = true)
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
    public OfficeLocation updateOffice(Long id, OfficeLocation updated) {
        OfficeLocation office = officeLocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));
        office.setName(updated.getName());
        office.setLatitude(updated.getLatitude());
        office.setLongitude(updated.getLongitude());
        office.setRadius(updated.getRadius());
        return officeLocationRepository.save(office);
    }

    @Transactional
    public void deleteOffice(Long id) {
        // Rule 5: Stronger delete checks
        if (attendanceSessionRepository.existsByOfficeId(id)
                || attendancePolicyRepository.findByOfficeId(id).isPresent()
                || !attendanceShiftRepository.findByOfficeId(id).isEmpty()) {
            throw new IllegalStateException("Cannot delete office. It is currently linked to sessions, policies, or shifts.");
        }
        officeLocationRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AttendancePolicyDTO> getAllPolicies() {
        return attendancePolicyRepository.findAll().stream()
                .map(attendanceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendancePolicy createPolicy(AttendancePolicyDTO dto) {
        validatePolicy(dto);
        OfficeLocation office = officeLocationRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        AttendancePolicy policy = attendancePolicyRepository.findByOfficeId(office.getId())
                .orElse(AttendancePolicy.builder().office(office).build());

        policy.setShortBreakStartTime(parseTime(dto.getShortBreakStartTime(), DEFAULT_SHORT_BREAK_START));
        policy.setShortBreakEndTime(parseTime(dto.getShortBreakEndTime(), DEFAULT_SHORT_BREAK_END));
        policy.setLongBreakStartTime(parseTime(dto.getLongBreakStartTime(), DEFAULT_LONG_BREAK_START));
        policy.setLongBreakEndTime(parseTime(dto.getLongBreakEndTime(), DEFAULT_LONG_BREAK_END));
        policy.setGracePeriodMinutes(dto.getGracePeriodMinutes() != null ? dto.getGracePeriodMinutes() : DEFAULT_GRACE_PERIOD);
        policy.setTrackingIntervalSec(dto.getTrackingIntervalSec() != null ? dto.getTrackingIntervalSec() : DEFAULT_TRACKING_INTERVAL);
        policy.setMaxAccuracyMeters(dto.getMaxAccuracyMeters());
        policy.setMinimumWorkMinutes(dto.getMinimumWorkMinutes());
        policy.setHalfDayMinutes(dto.getHalfDayMinutes());
        policy.setMaxIdleMinutes(dto.getMaxIdleMinutes());

        return attendancePolicyRepository.save(policy);
    }

    @Transactional
    public AttendancePolicy updatePolicy(Long id, AttendancePolicyDTO dto) {
        validatePolicy(dto);
        AttendancePolicy policy = attendancePolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));

        policy.setShortBreakStartTime(parseTime(dto.getShortBreakStartTime(), policy.getShortBreakStartTime()));
        policy.setShortBreakEndTime(parseTime(dto.getShortBreakEndTime(), policy.getShortBreakEndTime()));
        policy.setLongBreakStartTime(parseTime(dto.getLongBreakStartTime(), policy.getLongBreakStartTime()));
        policy.setLongBreakEndTime(parseTime(dto.getLongBreakEndTime(), policy.getLongBreakEndTime()));
        
        if (dto.getGracePeriodMinutes() != null) policy.setGracePeriodMinutes(dto.getGracePeriodMinutes());
        if (dto.getTrackingIntervalSec() != null) policy.setTrackingIntervalSec(dto.getTrackingIntervalSec());
        if (dto.getMaxAccuracyMeters() != null) policy.setMaxAccuracyMeters(dto.getMaxAccuracyMeters());
        if (dto.getMinimumWorkMinutes() != null) policy.setMinimumWorkMinutes(dto.getMinimumWorkMinutes());
        if (dto.getMaxIdleMinutes() != null) policy.setMaxIdleMinutes(dto.getMaxIdleMinutes());

        return attendancePolicyRepository.save(policy);
    }

    private LocalTime parseTime(String time, LocalTime defaultValue) {
        if (time == null || time.isEmpty()) return defaultValue;
        try {
            // Rule 7: Normalize (strip seconds/nanos)
            return LocalTime.parse(time).withSecond(0).withNano(0);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time format: " + time + ". Use HH:mm");
        }
    }

    private void validatePolicy(AttendancePolicyDTO dto) {
        LocalTime sbStart = parseTime(dto.getShortBreakStartTime(), DEFAULT_SHORT_BREAK_START);
        LocalTime sbEnd = parseTime(dto.getShortBreakEndTime(), DEFAULT_SHORT_BREAK_END);
        if (sbStart.isAfter(sbEnd)) throw new IllegalArgumentException("Short break start must be before end");

        LocalTime lbStart = parseTime(dto.getLongBreakStartTime(), DEFAULT_LONG_BREAK_START);
        LocalTime lbEnd = parseTime(dto.getLongBreakEndTime(), DEFAULT_LONG_BREAK_END);
        if (lbStart.isAfter(lbEnd)) throw new IllegalArgumentException("Long break start must be before end");

        // Rule 2: Improved Overlap Check (allows 13:00-13:30 and 13:30-14:00)
        if (sbStart.isBefore(lbEnd) && lbStart.isBefore(sbEnd)) {
            throw new IllegalArgumentException("Breaks must not overlap");
        }
    }

    @Transactional(readOnly = true)
    public List<AttendanceShift> getAllShifts() {
        return attendanceShiftRepository.findAll();
    }

    @Transactional
    public AttendanceShift createShift(AttendanceShift shift) {
        // Allow multiple shifts per office, but prevent duplicate names in the same office
        if (shift.getOffice() != null) {
            boolean duplicate = attendanceShiftRepository.findByOfficeId(shift.getOffice().getId())
                    .stream().anyMatch(s -> s.getName().equalsIgnoreCase(shift.getName()));
            if (duplicate) {
                throw new IllegalStateException("A shift with the name '" + shift.getName() + "' already exists for this office.");
            }
        }
        validateShift(shift);
        return attendanceShiftRepository.save(shift);
    }

    @Transactional
    public AttendanceShift updateShift(Long id, AttendanceShift updatedShift) {
        AttendanceShift shift = attendanceShiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        validateShift(updatedShift);
        
        shift.setName(updatedShift.getName());
        shift.setStartTime(updatedShift.getStartTime());
        shift.setEndTime(updatedShift.getEndTime());
        shift.setGraceMinutes(updatedShift.getGraceMinutes());
        shift.setMinHalfDayMinutes(updatedShift.getMinHalfDayMinutes());
        shift.setMinFullDayMinutes(updatedShift.getMinFullDayMinutes());
        shift.setShortBreakStartTime(updatedShift.getShortBreakStartTime());
        shift.setShortBreakEndTime(updatedShift.getShortBreakEndTime());
        shift.setLongBreakStartTime(updatedShift.getLongBreakStartTime());
        shift.setLongBreakEndTime(updatedShift.getLongBreakEndTime());
        shift.setOffice(updatedShift.getOffice());
        return attendanceShiftRepository.save(shift);
    }

    private void validateShift(AttendanceShift s) {
        // Rule 6: Null Safety cross-check
        validateTimesPair(s.getStartTime(), s.getEndTime(), "Shift");
        validateTimesPair(s.getShortBreakStartTime(), s.getShortBreakEndTime(), "Short Break");
        validateTimesPair(s.getLongBreakStartTime(), s.getLongBreakEndTime(), "Long Break");

        if (s.getStartTime() != null && s.getEndTime() != null) {
            if (s.getStartTime().isAfter(s.getEndTime())) throw new IllegalArgumentException("Shift start must be before end");

            // Rule 3: Breaks within shift
            if (s.getShortBreakStartTime() != null && (s.getShortBreakStartTime().isBefore(s.getStartTime()) || s.getShortBreakEndTime().isAfter(s.getEndTime()))) {
                throw new IllegalArgumentException("Short break must be within shift time");
            }
            if (s.getLongBreakStartTime() != null && (s.getLongBreakStartTime().isBefore(s.getStartTime()) || s.getLongBreakEndTime().isAfter(s.getEndTime()))) {
                throw new IllegalArgumentException("Long break must be within shift time");
            }

            // Rule 4: Min work vs Shift length
            long shiftMins = java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            if (s.getMinFullDayMinutes() > shiftMins) {
                throw new IllegalArgumentException("Minimum full day work minutes (" + s.getMinFullDayMinutes() + ") cannot exceed shift duration (" + shiftMins + ")");
            }
        }
    }

    private void validateTimesPair(LocalTime start, LocalTime end, String label) {
        if ((start != null && end == null) || (start == null && end != null)) {
            throw new IllegalArgumentException("Both start and end times are required for " + label);
        }
    }

    @Transactional
    public void deleteShift(Long id) {
        if (!attendanceShiftRepository.existsById(id)) throw new ResourceNotFoundException("Shift not found");
        attendanceShiftRepository.deleteById(id);
    }

    @Transactional
    public void deletePolicy(Long id) {
        AttendancePolicy policy = attendancePolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        
        // Rule 5: Safe delete check (Policy is linked to Office, Sessions are linked to Office)
        if (attendanceSessionRepository.existsByOfficeId(policy.getOffice().getId())) {
            throw new IllegalStateException("Policy is in use by sessions for office: " + policy.getOffice().getName());
        }
        attendancePolicyRepository.delete(policy);
    }
}
