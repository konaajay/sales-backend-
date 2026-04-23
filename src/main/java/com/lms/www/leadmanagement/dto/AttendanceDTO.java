package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDTO {
    private Long id;
    private Long userId;
    private String userName;
    private LocalDate date;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String status;
    private boolean isAutoCheckout;
    private Double lastLat;
    private Double lastLng;
    private LocalDateTime lastLocationTime;
    private LocalDateTime lastSeenTime;

    // Policy-driven fields for Frontend
    private Integer trackingIntervalSec;
    private String shortBreakStartTime;
    private String shortBreakEndTime;
    private String longBreakStartTime;
    private String longBreakEndTime;
    private Integer gracePeriodMinutes;
    private Integer outsideCount;
    private Double officeRadius;

    private Integer totalWorkMinutes;
    private String totalWorkHours; // Formatted "5h 30m"

    private Integer totalBreakMinutes;
    private String totalBreakHours;

    private Integer totalIdleMinutes;
    private String totalIdleHours;
}
