package com.lms.www.leadmanagement.mapper;

import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.AttendancePolicyDTO;
import com.lms.www.leadmanagement.dto.OfficeLocationDTO;
import com.lms.www.leadmanagement.entity.AttendanceSession;
import com.lms.www.leadmanagement.entity.AttendancePolicy;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class AttendanceMapper {

    public AttendanceDTO toDTO(AttendanceSession s, AttendancePolicy policy, int dayWorkMinutes, String dayWorkHours,
            LocalDate date) {
        if (s == null)
            return null;

        OfficeLocation office = s.getOffice();

        // Default values if policy or office is missing
        int trackingInterval = (policy != null && policy.getTrackingIntervalSec() != null)
                ? policy.getTrackingIntervalSec()
                : 300;
        String sBreakStart = (policy != null && policy.getShortBreakStartTime() != null)
                ? policy.getShortBreakStartTime().toString()
                : "17:00";
        String sBreakEnd = (policy != null && policy.getShortBreakEndTime() != null)
                ? policy.getShortBreakEndTime().toString()
                : "17:10";
        String lBreakStart = (policy != null && policy.getLongBreakStartTime() != null)
                ? policy.getLongBreakStartTime().toString()
                : "13:00";
        String lBreakEnd = (policy != null && policy.getLongBreakEndTime() != null)
                ? policy.getLongBreakEndTime().toString()
                : "14:00";
        int gracePeriod = (policy != null && policy.getGracePeriodMinutes() != null) ? policy.getGracePeriodMinutes()
                : 2;
        double radius = (office != null && office.getRadius() != null) ? office.getRadius() : 200.0;

        return AttendanceDTO.builder()
                .id(s.getId())
                .userId(s.getUser().getId())
                .userName(s.getUser().getName())
                .date(date)
                .checkInTime(s.getCheckInTime())
                .checkOutTime(s.getCheckOutTime())
                .status(s.getStatus().name())
                .isAutoCheckout(s.isAutoCheckout())
                .lastLat(s.getLastLat())
                .lastLng(s.getLastLng())
                .lastLocationTime(s.getLastLocationTime())
                .lastSeenTime(s.getLastSeenTime())
                .trackingIntervalSec(trackingInterval)
                .shortBreakStartTime(sBreakStart)
                .shortBreakEndTime(sBreakEnd)
                .longBreakStartTime(lBreakStart)
                .longBreakEndTime(lBreakEnd)
                .gracePeriodMinutes(gracePeriod)
                .outsideCount(s.getOutsideCount() != null ? s.getOutsideCount() : 0)
                .officeRadius(radius)
                .totalWorkMinutes(dayWorkMinutes)
                .totalWorkHours(dayWorkHours)
                .totalBreakMinutes(s.getTotalBreakMinutes())
                .totalBreakHours(String.format("%dh %dm",
                        (s.getTotalBreakMinutes() != null ? s.getTotalBreakMinutes() : 0) / 60,
                        (s.getTotalBreakMinutes() != null ? s.getTotalBreakMinutes() : 0) % 60))
                .totalIdleMinutes(s.getUnauthorizedOutsideMinutes() != null ? s.getUnauthorizedOutsideMinutes() : 0)
                .totalIdleHours(String.format("%dh %dm",
                        (s.getUnauthorizedOutsideMinutes() != null ? s.getUnauthorizedOutsideMinutes() : 0) / 60,
                        (s.getUnauthorizedOutsideMinutes() != null ? s.getUnauthorizedOutsideMinutes() : 0) % 60))
                .build();
    }

    public AttendanceDTO toDTO(AttendanceSession s) {
        if (s == null)
            return null;
        return toDTO(s, null, 0, "0h 0m", s.getCheckInTime().toLocalDate());
    }

    public OfficeLocationDTO toDTO(OfficeLocation o) {
        if (o == null)
            return null;
        return OfficeLocationDTO.builder()
                .id(o.getId())
                .name(o.getName())
                .latitude(o.getLatitude())
                .longitude(o.getLongitude())
                .radius(o.getRadius())
                .build();
    }

    public AttendancePolicyDTO toDTO(AttendancePolicy p) {
        if (p == null)
            return null;
        return AttendancePolicyDTO.builder()
                .id(p.getId())
                .officeId(p.getOffice() != null ? p.getOffice().getId() : null)
                .officeName(p.getOffice() != null ? p.getOffice().getName() : null)
                .shortBreakStartTime(p.getShortBreakStartTime() != null ? p.getShortBreakStartTime().toString() : null)
                .shortBreakEndTime(p.getShortBreakEndTime() != null ? p.getShortBreakEndTime().toString() : null)
                .longBreakStartTime(p.getLongBreakStartTime() != null ? p.getLongBreakStartTime().toString() : null)
                .longBreakEndTime(p.getLongBreakEndTime() != null ? p.getLongBreakEndTime().toString() : null)
                .gracePeriodMinutes(p.getGracePeriodMinutes())
                .trackingIntervalSec(p.getTrackingIntervalSec())
                .maxAccuracyMeters(p.getMaxAccuracyMeters())
                .minimumWorkMinutes(p.getMinimumWorkMinutes())
                .maxIdleMinutes(p.getMaxIdleMinutes())
                .build();
    }
}
