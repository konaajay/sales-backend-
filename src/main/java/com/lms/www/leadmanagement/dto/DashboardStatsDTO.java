package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    
    // Attendance
    private long presentCount;
    private long absentCount;
    private long halfDayCount;
    
    // Revenue
    private BigDecimal dailyRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal expectedRevenue;
    private BigDecimal pendingPaymentsAmount;
    private BigDecimal forecastRevenue;
    
    // Follow-ups
    private long todayFollowups;
    private long pendingFollowups;
    private long pendingPayments;
    private long pendingAppointments;
    
    // Target Metrics
    private BigDecimal monthlyTarget;
    private Double targetAchievement; // Percentage
    private long totalLostCount;
    private long interestedCount;
    private long interestedToday;
    private long totalLeads;
    private long convertedCount;
    private long totalUsers;

    // Distribution & Performance
    private java.util.Map<String, Long> statusDistribution;
    private java.util.Map<String, Long> userBreakdown;
    private java.util.List<MemberPerformanceDTO> performance;
    private java.util.List<java.util.Map<String, Object>> dailyTrend;

    // Additional Detail Metrics
    private long todayLeadsCount;
    private long todayPaymentsCount;
    private long completedToday;
    private long highPriorityFollowups;
    private long activeSupportTickets;
    private long pendingSupportTickets;
    private long resolvedSupportTickets;
    private long closedSupportTickets;
    private long totalPendingCount;
    private long pendingLeadsCount;
    private long overduePaymentsCount;
    private java.math.BigDecimal pendingRevenueAmount;
}
