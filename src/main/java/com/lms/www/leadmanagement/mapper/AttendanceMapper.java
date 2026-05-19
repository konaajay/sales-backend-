package com.lms.www.leadmanagement.mapper;

import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.OfficeLocationDTO;
import com.lms.www.leadmanagement.entity.AttendanceSession;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class AttendanceMapper {

    public AttendanceDTO toDTO(AttendanceSession s, int dayWorkMinutes, String dayWorkHours,
            LocalDate date) {
        if (s == null)
            return null;

        OfficeLocation office = s.getOffice();

        int trackingInterval = (office != null && office.getTrackingIntervalSec() != null)
                ? office.getTrackingIntervalSec()
                : 300;
        
        String sBreakStart = (s.getUser().getShift() != null && s.getUser().getShift().getShortBreakStartTime() != null)
                ? s.getUser().getShift().getShortBreakStartTime().toString()
                : "17:00";
        String sBreakEnd = (s.getUser().getShift() != null && s.getUser().getShift().getShortBreakEndTime() != null)
                ? s.getUser().getShift().getShortBreakEndTime().toString()
                : "17:10";
        String lBreakStart = (s.getUser().getShift() != null && s.getUser().getShift().getLongBreakStartTime() != null)
                ? s.getUser().getShift().getLongBreakStartTime().toString()
                : "13:00";
        String lBreakEnd = (s.getUser().getShift() != null && s.getUser().getShift().getLongBreakEndTime() != null)
                ? s.getUser().getShift().getLongBreakEndTime().toString()
                : "14:00";

        String shiftStart = (s.getUser().getShift() != null) ? s.getUser().getShift().getStartTime().toString()
                : "09:30";
        String shiftEnd = (s.getUser().getShift() != null) ? s.getUser().getShift().getEndTime().toString()
                : "18:30";
        int gracePeriod = (s.getUser().getShift() != null) ? s.getUser().getShift().getGraceMinutes()
                : 2;
        double radius = (office != null && office.getRadius() != null) ? office.getRadius() : 200.0;

        long workSecs = s.getTotalWorkSeconds() != null ? s.getTotalWorkSeconds() : 0L;
        long breakSecs = s.getTotalBreakSeconds() != null ? s.getTotalBreakSeconds() : 0L;
        long outsideSecs = s.getTotalOutsideSeconds() != null ? s.getTotalOutsideSeconds() : 0L;

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
                .lastSeenTime(s.getLastSeenTime())
                .trackingIntervalSec(trackingInterval)
                .shortBreakStartTime(sBreakStart)
                .shortBreakEndTime(sBreakEnd)
                .longBreakStartTime(lBreakStart)
                .longBreakEndTime(lBreakEnd)
                .shiftStartTime(shiftStart)
                .shiftEndTime(shiftEnd)
                .gracePeriodMinutes(gracePeriod)
                .officeRadius(radius)
                .officeLat(office != null ? office.getLatitude() : null)
                .officeLng(office != null ? office.getLongitude() : null)
                .officeName(office != null ? office.getName() : null)
                .totalWorkMinutes((int)(workSecs / 60))
                .totalWorkHours(String.format("%dh %dm", workSecs / 3600, (workSecs % 3600) / 60))
                .totalBreakMinutes((int)(breakSecs / 60))
                .totalBreakHours(String.format("%dh %dm", breakSecs / 3600, (breakSecs % 3600) / 60))
                .totalIdleMinutes((int)(outsideSecs / 60))
                .totalIdleHours(String.format("%dh %dm", outsideSecs / 3600, (outsideSecs % 3600) / 60))
                .lateMinutes(s.getLateMinutes() != null ? s.getLateMinutes() : 0)
                .productiveMinutes((int)(workSecs / 60))
                .late(s.isLate())
                .loginTime(s.getCheckInTime())
                .logoutTime(s.getCheckOutTime())
                .build();
    }

    public AttendanceDTO toDTO(AttendanceSession s) {
        if (s == null)
            return null;
        int mins = (int)((s.getTotalWorkSeconds() != null ? s.getTotalWorkSeconds() : 0L) / 60);
        String hours = String.format("%dh %dm", mins / 60, mins % 60);
        return toDTO(s, mins, hours, s.getCheckInTime() != null ? s.getCheckInTime().toLocalDate() : LocalDate.now());
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
}
