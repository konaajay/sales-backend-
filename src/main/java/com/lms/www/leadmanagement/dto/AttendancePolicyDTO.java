package com.lms.www.leadmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePolicyDTO {
    private Long id;

    @NotNull(message = "Office ID is required")
    private Long officeId;

    private String officeName;

    @NotBlank(message = "Short break start time is required")
    private String shortBreakStartTime;

    @NotBlank(message = "Short break end time is required")
    private String shortBreakEndTime;

    @NotBlank(message = "Long break start time is required")
    private String longBreakStartTime;

    @NotBlank(message = "Long break end time is required")
    private String longBreakEndTime;

    @NotNull(message = "Grace period is required")
    @Min(value = 0, message = "Grace period must be non-negative")
    private Integer gracePeriodMinutes;

    @NotNull(message = "Tracking interval is required")
    @Min(value = 10, message = "Tracking interval must be at least 10 seconds")
    private Integer trackingIntervalSec;

    @NotNull(message = "Max accuracy is required")
    @Min(value = 1, message = "Max accuracy must be at least 1 meter")
    private Integer maxAccuracyMeters;

    @NotNull(message = "Minimum work minutes is required")
    @Min(value = 1, message = "Minimum work minutes must be at least 1")
    private Integer minimumWorkMinutes;

    @NotNull(message = "Max idle minutes is required")
    @Min(value = 1, message = "Max idle minutes must be at least 1")
    private Integer maxIdleMinutes;
}

