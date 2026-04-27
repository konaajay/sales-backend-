package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePreviewRequest {
    private Long userId;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    
    // Break timings (Optional, can fall back to policy)
    private LocalTime longBreakStart;
    private LocalTime longBreakEnd;
    private LocalTime shortBreakStart;
    private LocalTime shortBreakEnd;
    
    // Thresholds (Optional, can fall back to policy/shift)
    private Integer minFullDayMinutes;
    private Integer minHalfDayMinutes;
    
    // For Late calculation
    private LocalTime shiftStart;
    private Integer graceMinutes;
}
