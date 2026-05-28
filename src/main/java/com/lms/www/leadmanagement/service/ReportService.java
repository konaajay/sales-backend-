package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.ReportFilterDTO;
import com.lms.www.leadmanagement.dto.TimeSeriesStatsDTO;
import com.lms.www.leadmanagement.dto.LeadStatsDTO;
import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.ReportScope;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityService securityService;

    public User getCurrentUser() {
        return securityService.getCurrentUser();
    }

    private java.util.Set<Long> determineAllowedUserIds(User loggedInUser, ReportFilterDTO filter) {
        java.util.Set<Long> allowedIds = securityService.getAllowedUserIds(loggedInUser);

        if (filter.getUserId() != null) {
            securityService.validateAccess(loggedInUser, filter.getUserId());
            return java.util.Collections.singleton(filter.getUserId());
        } else if (filter.getTeamLeaderId() != null) {
            securityService.validateAccess(loggedInUser, filter.getTeamLeaderId());
            java.util.Set<Long> teamIds = new java.util.HashSet<>(userRepository.findSubordinateIds(filter.getTeamLeaderId()));
            teamIds.add(filter.getTeamLeaderId());
            return teamIds;
        } else if (filter.getManagerId() != null) {
            securityService.validateAccess(loggedInUser, filter.getManagerId());
            java.util.Set<Long> managerIds = new java.util.HashSet<>(userRepository.findSubordinateIds(filter.getManagerId()));
            managerIds.add(filter.getManagerId());
            return managerIds;
        }

        return allowedIds;
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public LeadStatsDTO getFilteredStats(ReportFilterDTO filter) {
        User loggedInUser = getCurrentUser();
        java.util.Set<Long> allowedIds = determineAllowedUserIds(loggedInUser, filter);
        boolean isGlobalAdmin = securityService.isAdmin(loggedInUser) && filter.getUserId() == null && filter.getTeamLeaderId() == null && filter.getManagerId() == null;

        LocalDateTime start = filter.getFromDate() != null ? filter.getFromDate().atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = filter.getToDate() != null ? filter.getToDate().atTime(LocalTime.MAX) : LocalDateTime.now();

        Map<String, Object> stats;
        double totalRevenue = 0;
        long convertedCount = 0;

        if (!isGlobalAdmin) {
            List<Long> ids = new java.util.ArrayList<>(allowedIds);
            stats = ids.isEmpty() ? new HashMap<>() : leadRepository.getSummaryStats(ids, start, end);
            if (!ids.isEmpty()) {
                List<Map<String, Object>> revData = paymentRepository.getRevenuePerUser(ids, start, end);
                for (Map<String, Object> map : revData) {
                    totalRevenue += map.get("amount") != null ? ((Number) map.get("amount")).doubleValue() : 0.0;
                    convertedCount += map.get("successCount") != null ? ((Number) map.get("successCount")).longValue() : 0L;
                }
            }
        } else {
            stats = leadRepository.getGlobalSummaryStats(start, end);
            totalRevenue = paymentRepository.getGlobalTotalRevenue(start, end).doubleValue();
            
            // For global converted count, sum across all successful payments in period
            List<Map<String, Object>> globalRev = paymentRepository.getRevenuePerUser(null, start, end);
             for (Map<String, Object> map : globalRev) {
                convertedCount += map.get("successCount") != null ? ((Number) map.get("successCount")).longValue() : 0L;
            }
        }

        return LeadStatsDTO.builder()
                .total(asLong(stats.get("total")))
                .newCount(asLong(stats.get("newCount")))
                .interestedCount(asLong(stats.get("interestedCount")))
                .contactedCount(asLong(stats.get("contactedCount")))
                .followUpCount(asLong(stats.get("followUpCount")))
                .convertedCount(convertedCount)
                .lostCount(asLong(stats.get("lostCount")))
                .totalRevenue(totalRevenue)
                .build();
    }

    private long asLong(Object val) {
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0L;
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public List<TimeSeriesStatsDTO> getFilteredTrend(ReportFilterDTO filter) {
        User loggedInUser = getCurrentUser();
        java.util.Set<Long> allowedIds = determineAllowedUserIds(loggedInUser, filter);
        boolean isGlobalAdmin = securityService.isAdmin(loggedInUser) && filter.getUserId() == null && filter.getTeamLeaderId() == null && filter.getManagerId() == null;
        List<Long> userIds = !isGlobalAdmin ? new java.util.ArrayList<>(allowedIds) : null;

        LocalDateTime start = filter.getFromDate() != null ? filter.getFromDate().atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = filter.getToDate() != null ? filter.getToDate().atTime(LocalTime.MAX) : LocalDateTime.now();

        // Guard clause to prevent IN () database syntax error
        if (!isGlobalAdmin && (userIds == null || userIds.isEmpty())) {
            return new ArrayList<>();
        }

        List<Long> safeUserIds = (userIds == null || userIds.isEmpty()) ? List.of(-1L) : userIds;

        // Fetch daily lead counts (Generated)
        List<com.lms.www.leadmanagement.dto.TrendProjection> leadTrend = leadRepository.getDailyLeadTrend(isGlobalAdmin, safeUserIds, start, end);

        // Fetch daily lost counts
        List<String> lostStatuses = List.of("LOST", "NOT_INTERESTED", "REJECTED");
        List<com.lms.www.leadmanagement.dto.TrendProjection> lostTrend = leadRepository.getDailyLostTrend(isGlobalAdmin, safeUserIds, lostStatuses, start, end);

        // Fetch daily converted counts
        List<String> successStatuses = List.of("CONVERTED", "PAID", "EMI", "SUCCESS");
        List<com.lms.www.leadmanagement.dto.TrendProjection> convertedTrend = leadRepository.getDailyConvertedTrend(isGlobalAdmin, safeUserIds, successStatuses, start, end);

        // Fetch daily revenue (Amount)
        List<com.lms.www.leadmanagement.entity.Payment> payments;
        if (userIds != null) {
            payments = userIds.isEmpty() ? new ArrayList<>()
                    : paymentRepository.findFilteredByUserIds(userIds, start, end);
        } else {
            payments = paymentRepository.findByCreatedAtBetween(start, end);
        }

        Map<LocalDate, Long> leadsByDate = new HashMap<>();
        for (com.lms.www.leadmanagement.dto.TrendProjection row : leadTrend) {
            if (row.getDate() != null) {
                LocalDate date = row.getDate() instanceof java.sql.Date ? ((java.sql.Date) row.getDate()).toLocalDate() : new java.sql.Date(row.getDate().getTime()).toLocalDate();
                leadsByDate.put(date, row.getCount() != null ? row.getCount() : 0L);
            }
        }

        Map<LocalDate, Long> lostByDate = new HashMap<>();
        for (com.lms.www.leadmanagement.dto.TrendProjection row : lostTrend) {
            if (row.getDate() != null) {
                LocalDate date = row.getDate() instanceof java.sql.Date ? ((java.sql.Date) row.getDate()).toLocalDate() : new java.sql.Date(row.getDate().getTime()).toLocalDate();
                lostByDate.put(date, row.getCount() != null ? row.getCount() : 0L);
            }
        }

        Map<LocalDate, Long> convertedByDate = new HashMap<>();
        for (com.lms.www.leadmanagement.dto.TrendProjection row : convertedTrend) {
            if (row.getDate() != null) {
                LocalDate date = row.getDate() instanceof java.sql.Date ? ((java.sql.Date) row.getDate()).toLocalDate() : new java.sql.Date(row.getDate().getTime()).toLocalDate();
                convertedByDate.put(date, row.getCount() != null ? row.getCount() : 0L);
            }
        }

        Map<LocalDate, BigDecimal> revenueByDate = payments.stream()
                .filter(p -> p.getStatus() == com.lms.www.leadmanagement.entity.Payment.Status.PAID
                        || p.getStatus() == com.lms.www.leadmanagement.entity.Payment.Status.APPROVED)
                .collect(Collectors.groupingBy(
                        p -> p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : LocalDate.of(1970, 1, 1),
                        Collectors.reducing(BigDecimal.ZERO, p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add)));

        List<TimeSeriesStatsDTO> result = new ArrayList<>();
        LocalDate current = start.toLocalDate();
        LocalDate stop = end.toLocalDate();

        while (!current.isAfter(stop)) {
            result.add(TimeSeriesStatsDTO.builder()
                    .date(current)
                    .leadsCount(leadsByDate.getOrDefault(current, 0L))
                    .lostCount(lostByDate.getOrDefault(current, 0L))
                    .convertedCount(convertedByDate.getOrDefault(current, 0L))
                    .revenue(revenueByDate.getOrDefault(current, BigDecimal.ZERO))
                    .build());
            current = current.plusDays(1);
        }

        return result;
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public List<LeadDTO> getTodayFollowups(ReportFilterDTO filter) {
        User loggedInUser = getCurrentUser();
        java.util.Set<Long> allowedIds = determineAllowedUserIds(loggedInUser, filter);
        boolean isGlobalAdmin = securityService.isAdmin(loggedInUser) && filter.getUserId() == null && filter.getTeamLeaderId() == null;

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        return leadRepository.findAll().stream()
                .filter(l -> l.getFollowUpDate() != null &&
                        !l.getFollowUpDate().isBefore(startOfDay) &&
                        !l.getFollowUpDate().isAfter(endOfDay) &&
                        (isGlobalAdmin || (l.getAssignedTo() != null && allowedIds.contains(l.getAssignedTo().getId()))))
                .map(LeadDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
