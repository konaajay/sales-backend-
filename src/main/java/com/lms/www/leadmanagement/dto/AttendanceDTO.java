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
    private LocalDateTime breakStartTime;

    // Policy-driven fields for Frontend
    private Integer trackingIntervalSec;
    private String shortBreakStartTime;
    private String shortBreakEndTime;
    private String longBreakStartTime;
    private String longBreakEndTime;
    private String shiftStartTime;
    private String shiftEndTime;
    private Integer gracePeriodMinutes;

    private Integer outsideCount;
    private Double officeRadius;
    private Double officeLat;
    private Double officeLng;
    private String officeName;

    private Integer totalWorkMinutes;
    private String totalWorkHours; // Formatted "5h 30m"

    private Integer totalBreakMinutes;
    private String totalBreakHours;

    private Integer totalIdleMinutes;
    private String totalIdleHours;

    private Integer lateMinutes;
    private Integer productiveMinutes;
    private Integer shortBreakMinutes;
    private Integer longBreakMinutes;
    private boolean late;
    
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;

    private String note;
    
    // WFH Fields
    private boolean isWfhApproved;
    private String wfhStatus;
    private Integer requestedDays;
}
