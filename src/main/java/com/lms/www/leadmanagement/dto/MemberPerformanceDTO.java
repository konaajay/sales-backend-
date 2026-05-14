package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPerformanceDTO {
    private Long userId;
    private String username;
    private String role;
    private long totalLeads;
    private long convertedCount;
    private long lostCount;
    private long presentCount;
    private long absentCount;
    private long lateCount;
    private java.math.BigDecimal targetAmount;
    private java.math.BigDecimal monthlyRevenue;
}
