package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatsDTO {
    private long total;
    private long newCount;
    private long contactedCount;
    private long interestedCount;
    private long followUpCount;
    private long convertedCount;
    private long lostCount;
    private long closedCount;
    private double totalRevenue;
}
