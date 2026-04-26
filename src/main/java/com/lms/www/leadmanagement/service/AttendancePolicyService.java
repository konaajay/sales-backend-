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
        if (attendanceSessionRepository.existsByOfficeId(id)) {
            throw new IllegalStateException("Cannot delete office with active or historical attendance logs.");
        }
        attendancePolicyRepository.deleteByOfficeId(id);
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
        OfficeLocation office = officeLocationRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        AttendancePolicy policy = AttendancePolicy.builder()
                .office(office)
                .shortBreakStartTime(dto.getShortBreakStartTime() != null ? LocalTime.parse(dto.getShortBreakStartTime()) : DEFAULT_SHORT_BREAK_START)
                .shortBreakEndTime(dto.getShortBreakEndTime() != null ? LocalTime.parse(dto.getShortBreakEndTime()) : DEFAULT_SHORT_BREAK_END)
                .longBreakStartTime(dto.getLongBreakStartTime() != null ? LocalTime.parse(dto.getLongBreakStartTime()) : DEFAULT_LONG_BREAK_START)
                .longBreakEndTime(dto.getLongBreakEndTime() != null ? LocalTime.parse(dto.getLongBreakEndTime()) : DEFAULT_LONG_BREAK_END)
                .gracePeriodMinutes(dto.getGracePeriodMinutes() != null ? dto.getGracePeriodMinutes() : DEFAULT_GRACE_PERIOD)
                .trackingIntervalSec(dto.getTrackingIntervalSec() != null ? dto.getTrackingIntervalSec() : DEFAULT_TRACKING_INTERVAL)
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

        if (dto.getShortBreakStartTime() != null) policy.setShortBreakStartTime(LocalTime.parse(dto.getShortBreakStartTime()));
        if (dto.getShortBreakEndTime() != null) policy.setShortBreakEndTime(LocalTime.parse(dto.getShortBreakEndTime()));
        if (dto.getLongBreakStartTime() != null) policy.setLongBreakStartTime(LocalTime.parse(dto.getLongBreakStartTime()));
        if (dto.getLongBreakEndTime() != null) policy.setLongBreakEndTime(LocalTime.parse(dto.getLongBreakEndTime()));
        if (dto.getGracePeriodMinutes() != null) policy.setGracePeriodMinutes(dto.getGracePeriodMinutes());
        if (dto.getTrackingIntervalSec() != null) policy.setTrackingIntervalSec(dto.getTrackingIntervalSec());
        if (dto.getMaxAccuracyMeters() != null) policy.setMaxAccuracyMeters(dto.getMaxAccuracyMeters());
        if (dto.getMinimumWorkMinutes() != null) policy.setMinimumWorkMinutes(dto.getMinimumWorkMinutes());
        if (dto.getMaxIdleMinutes() != null) policy.setMaxIdleMinutes(dto.getMaxIdleMinutes());

        return attendancePolicyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public List<AttendanceShift> getAllShifts() {
        return attendanceShiftRepository.findAll();
    }

    @Transactional
    public AttendanceShift createShift(AttendanceShift shift) {
        return attendanceShiftRepository.save(shift);
    }

    @Transactional
    public AttendanceShift updateShift(Long id, AttendanceShift updatedShift) {
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
        if (!attendanceShiftRepository.existsById(id)) throw new ResourceNotFoundException("Shift not found");
        attendanceShiftRepository.deleteById(id);
    }

    @Transactional
    public void deletePolicy(Long id) {
        if (!attendancePolicyRepository.existsById(id)) throw new ResourceNotFoundException("Policy not found");
        attendancePolicyRepository.deleteById(id);
    }
}
