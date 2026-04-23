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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Logged in user not found"));
    }

    private void collectSubordinates(User user, Set<User> collector) {
        if (user.getSubordinates() != null) {
            for (User sub : user.getSubordinates()) {
                if (!collector.contains(sub)) {
                    collector.add(sub);
                    collectSubordinates(sub, collector);
                }
            }
        }
        if (user.getManagedAssociates() != null) {
            for (User assoc : user.getManagedAssociates()) {
                if (!collector.contains(assoc)) {
                    collector.add(assoc);
                    collectSubordinates(assoc, collector);
                }
            }
        }
    }

    private Collection<User> determineAllowedUsers(User loggedInUser, ReportFilterDTO filter) {
        Set<User> users = new HashSet<>();

        if (filter.getUserId() != null) {
            Long uId = filter.getUserId();
            if (uId == null) throw new IllegalArgumentException("User ID from filter is null");
            userRepository.findById(uId).ifPresent(users::add);
            return users;
        } else if (filter.getTeamLeaderId() != null) {
            Long tlId = filter.getTeamLeaderId();
            if (tlId == null) throw new IllegalArgumentException("Team Leader ID from filter is null");
            userRepository.findById(tlId).ifPresent(tl -> {
                users.add(tl);
                collectSubordinates(tl, users);
            });
            return users;
        }

        // Default: Scope-based
        ReportScope scope = loggedInUser.getReportScope();
        if (scope == null)
            scope = ReportScope.OWN;

        if (scope == ReportScope.ALL) {
            return null; // Signals 'all users'
        } else if (scope == ReportScope.TEAM) {
            users.add(loggedInUser);
            collectSubordinates(loggedInUser, users);
        } else {
            users.add(loggedInUser);
        }

        return users;
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public LeadStatsDTO getFilteredStats(ReportFilterDTO filter) {
        User loggedInUser = getCurrentUser();
        Collection<User> allowedUsers = determineAllowedUsers(loggedInUser, filter);

        LocalDateTime start = filter.getFromDate() != null ? filter.getFromDate().atStartOfDay()
                : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = filter.getToDate() != null ? filter.getToDate().atTime(LocalTime.MAX) : LocalDateTime.now();

        Map<String, Long> stats;
        double totalRevenue = 0;
        long convertedCount = 0;

        if (allowedUsers != null) {
            stats = allowedUsers.isEmpty() ? new HashMap<>() : leadRepository.getSummaryStats(allowedUsers, start, end);
            List<Long> ids = allowedUsers.stream().map(User::getId).collect(Collectors.toList());
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
        Collection<User> allowedUsers = determineAllowedUsers(loggedInUser, filter);
        List<Long> userIds = allowedUsers != null ? allowedUsers.stream().map(User::getId).collect(Collectors.toList())
                : null;

        LocalDateTime start = filter.getFromDate() != null ? filter.getFromDate().atStartOfDay()
                : LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = filter.getToDate() != null ? filter.getToDate().atTime(LocalTime.MAX) : LocalDateTime.now();

        // Fetch daily lead counts (Generated)
        List<Map<String, Object>> leadTrend;
        if (userIds != null) {
            leadTrend = userIds.isEmpty() ? new ArrayList<>() : leadRepository.getDailyLeadTrend(userIds, start, end);
        } else {
            leadTrend = leadRepository.getGlobalDailyLeadTrend(start, end);
        }

        // Fetch daily lost counts
        List<Map<String, Object>> lostTrend;
        if (userIds != null) {
            lostTrend = userIds.isEmpty() ? new ArrayList<>() : leadRepository.getDailyLostTrend(userIds, start, end);
        } else {
            lostTrend = leadRepository.getGlobalDailyLostTrend(start, end);
        }

        // Fetch daily revenue (Amount)
        List<com.lms.www.leadmanagement.entity.Payment> payments;
        if (userIds != null) {
            payments = userIds.isEmpty() ? new ArrayList<>()
                    : paymentRepository.findFilteredByUserIds(userIds, start, end);
        } else {
            payments = paymentRepository.findByCreatedAtBetween(start, end);
        }

        Map<LocalDate, Long> leadsByDate = new HashMap<>();
        for (Map<String, Object> row : leadTrend) {
            Object dateObj = row.get("date");
            LocalDate date = null;
            if (dateObj instanceof java.sql.Date)
                date = ((java.sql.Date) dateObj).toLocalDate();
            else if (dateObj instanceof java.time.LocalDate)
                date = (java.time.LocalDate) dateObj;

            if (date != null) {
                Object countObj = row.get("count");
                long count = 0;
                if (countObj instanceof Number) {
                    count = ((Number) countObj).longValue();
                }
                leadsByDate.put(date, count);
            }
        }

        Map<LocalDate, Long> lostByDate = new HashMap<>();
        for (Map<String, Object> row : lostTrend) {
            Object dateObj = row.get("date");
            LocalDate date = null;
            if (dateObj instanceof java.sql.Date)
                date = ((java.sql.Date) dateObj).toLocalDate();
            else if (dateObj instanceof java.time.LocalDate)
                date = (java.time.LocalDate) dateObj;

            if (date != null) {
                Object countObj = row.get("count");
                long count = 0;
                if (countObj instanceof Number) {
                    count = ((Number) countObj).longValue();
                }
                lostByDate.put(date, count);
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
                    .revenue(revenueByDate.getOrDefault(current, BigDecimal.ZERO))
                    .build());
            current = current.plusDays(1);
        }

        return result;
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    public List<LeadDTO> getTodayFollowups(ReportFilterDTO filter) {
        User loggedInUser = getCurrentUser();
        Collection<User> allowedUsers = determineAllowedUsers(loggedInUser, filter);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        return leadRepository.findAll().stream()
                .filter(l -> l.getFollowUpDate() != null &&
                        !l.getFollowUpDate().isBefore(startOfDay) &&
                        !l.getFollowUpDate().isAfter(endOfDay) &&
                        (allowedUsers == null || allowedUsers.contains(l.getAssignedTo())))
                .map(LeadDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
