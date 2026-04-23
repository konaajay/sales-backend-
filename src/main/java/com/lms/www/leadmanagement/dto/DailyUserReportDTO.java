package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserReportDTO {
    private Long userId;
    private String userName;
    private long callCount;
    private long totalDuration;
    private double avgDuration;
    private boolean flagged;
    private String suspicionReason;
}
