package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePreviewResponse {
    private long workedMinutes;
    private long breakMinutes;
    private long effectiveMinutes;
    private String status;
    private boolean isLate;
}
