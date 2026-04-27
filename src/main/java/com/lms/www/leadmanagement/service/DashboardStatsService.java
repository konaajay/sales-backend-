package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.*;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardStatsService {

    @Autowired
    private AttendanceSessionRepository attendanceRepository;

    @Autowired
    private AttendanceDailyRepository attendanceDailyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LeadTaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RevenueTargetRepository targetRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PipelineStageRepository pipelineStageRepository;

    @Autowired
    private TicketRepository ticketRepository;

    public DashboardSummaryDTO getUnifiedSummary(User user, LocalDate from, LocalDate to, Long targetUserId, Long teamId) {
        if (user == null) return null;
        String viewerRole = (user.getRole() != null) ? user.getRole().getName() : "ASSOCIATE";

        LocalDateTime start = (from != null ? from : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime end = (to != null ? to : LocalDate.now()).atTime(LocalTime.MAX);

        // Security / Scoping Logic
        Collection<User> allowedUsers = determineAllowedUsers(user, targetUserId, teamId);
        // Use consistent date defaults
        LocalDate fromDate = (from != null) ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = (to != null) ? to : LocalDate.now();

        // 1. Basic Stats
        boolean isGlobalAdmin = viewerRole.equals("ADMIN") && targetUserId == null && teamId == null;
        DashboardStatsDTO stats = getStats(allowedUsers, fromDate, toDate, isGlobalAdmin, user, targetUserId, teamId);
        // 2. Trend Data (Revenue, Leads, Lost)
        ReportFilterDTO filter = ReportFilterDTO.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        if (targetUserId != null) filter.setUserId(targetUserId);
        else if (teamId != null) filter.setTeamLeaderId(teamId);
        
        List<TimeSeriesStatsDTO> trend = reportService.getFilteredTrend(filter);

        // 3. Status Distribution (Pre-aggregated)
        String currentViewerRole = viewerRole;
        List<Long> userIds = allowedUsers.stream().map(User::getId).collect(Collectors.toList());
        
        // 3. Status Distribution (Pre-aggregated & Dynamic)
        List<DashboardProjection> distributionList;
        if (viewerRole.equals("ADMIN") && targetUserId == null && teamId == null) {
            distributionList = leadRepository.countByStatusGlobal(start, end);
        } else {
            distributionList = leadRepository.countByStatusForUsers(userIds, start, end);
        }

        Map<String, Long> mappedDistribution = new HashMap<>();
        for (DashboardProjection p : distributionList) {
            if (p.getStatus() != null) {
                mappedDistribution.put(p.getStatus().toUpperCase(), p.getCount());
            }
        }

        // Add legacy keys for frontend compatibility if missing
        mappedDistribution.putIfAbsent("NEW", 0L);
        mappedDistribution.putIfAbsent("CONTACTED", 0L);
        mappedDistribution.putIfAbsent("FOLLOW_UP", mappedDistribution.getOrDefault("FOLLOWUP", 0L));
        mappedDistribution.putIfAbsent("CONVERTED", mappedDistribution.getOrDefault("PAID", 0L) + mappedDistribution.getOrDefault("SUCCESS", 0L));

        return DashboardSummaryDTO.builder()
                .stats(stats)
                .trend(trend)
                .statusDistribution(mappedDistribution)
                .performance(stats.getPerformance())
                .build();
    }

    public Map<String, Long> getGlobalStats() {
        LocalDateTime start = LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        Map<String, Long> stats = leadRepository.getGlobalSummaryStats(start, end);
        Map<String, Long> result = new HashMap<>();
        if (stats != null) {
            result.put("NEW", asLong(stats.get("newCount")));
            result.put("CONTACTED", asLong(stats.get("contactedCount")));
            result.put("INTERESTED", asLong(stats.get("interestedCount")));
            result.put("FOLLOW_UP", asLong(stats.get("followUpCount")));
            result.put("CONVERTED", asLong(stats.get("convertedCount")));
            result.put("LOST", asLong(stats.get("lostCount")));
        }
        return result;
    }

    public Map<String, Object> getStats(LocalDateTime start, LocalDateTime end, User requester, Long userId) {
        LocalDate from = start != null ? start.toLocalDate() : LocalDate.now().minusDays(30);
        LocalDate to = end != null ? end.toLocalDate() : LocalDate.now();
        DashboardSummaryDTO summary = getUnifiedSummary(requester, from, to, userId, null);
        
        Map<String, Object> result = new HashMap<>();
        if (summary != null) {
            result.put("stats", summary.getStats());
            result.put("statusDistribution", summary.getStatusDistribution());
            result.put("performance", summary.getPerformance());
            result.put("trend", summary.getTrend());
        }
        return result;
    }

    public List<Map<String, Object>> getMemberPerformanceFiltered(LocalDateTime start, LocalDateTime end, User requester, Long userId, Long tlId) {
        LocalDate from = start != null ? start.toLocalDate() : LocalDate.now().minusDays(30);
        LocalDate to = end != null ? end.toLocalDate() : LocalDate.now();
        DashboardSummaryDTO summary = getUnifiedSummary(requester, from, to, userId, tlId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        if (summary != null && summary.getPerformance() != null) {
            for (MemberPerformanceDTO perf : summary.getPerformance()) {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", perf.getUserId());
                map.put("username", perf.getUsername());
                map.put("role", perf.getRole());
                map.put("totalLeads", perf.getTotalLeads());
                map.put("convertedCount", perf.getConvertedCount());
                map.put("lostCount", perf.getLostCount());
                result.add(map);
            }
        }
        return result;
    }

    private long asLong(Object val) {
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0L;
    }

    public Collection<User> determineAllowedUsers(User requester, Long targetUserId, Long teamId) {
        Set<User> users = new HashSet<>();
        String role = (requester.getRole() != null) ? requester.getRole().getName() : "ASSOCIATE";

        if (role.equals("ADMIN")) {
            if (targetUserId != null) {
                userRepository.findById(targetUserId).ifPresent(target -> {
                    users.add(target);
                    collectSubordinates(target, users);
                });
            } else if (teamId != null) {
                userRepository.findById(teamId).ifPresent(tl -> {
                    users.add(tl);
                    collectSubordinates(tl, users);
                });
            } else {
                return Collections.emptyList();
            }
        } else if (role.equals("MANAGER") || role.equals("TEAM_LEADER")) {
            if (targetUserId != null) {
                userRepository.findById(targetUserId).ifPresent(target -> {
                    // For Admin, always allow. For Manager/TL, only if they have permission to see them
                    if (role.equals("ADMIN")) {
                        users.add(target);
                    } else {
                        Set<User> subordinates = new HashSet<>();
                        collectSubordinates(requester, subordinates);
                        // Fallback: If they manage this user directly, include them even if recursion is messy
                        boolean isDirectSub = requester.getManagedAssociates() != null && requester.getManagedAssociates().contains(target);
                        if (subordinates.contains(target) || requester.getId().equals(target.getId()) || isDirectSub) {
                            users.add(target);
                        }
                    }
                });
            } else if (teamId != null) {
                userRepository.findById(teamId).ifPresent(tl -> {
                    Set<User> subordinates = new HashSet<>();
                    collectSubordinates(requester, subordinates);
                    if (subordinates.contains(tl) || requester.getId().equals(tl.getId())) {
                        users.add(tl);
                        collectSubordinates(tl, users);
                    }
                });
            } else {
                users.add(requester);
                collectSubordinates(requester, users);
            }
        } else {
            users.add(requester);
        }

        return users;
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

    public DashboardStatsDTO getStats(Collection<User> allowedUsers, LocalDate from, LocalDate to, boolean isGlobalAdmin, User requester, Long targetUserId, Long teamId) {
        if (requester == null)
            return null;

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime zdtNow = ZonedDateTime.now(zone);
        LocalDateTime now = zdtNow.toLocalDateTime();
        
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);
        
        // Define 'Today' range for the schedule card
        LocalDateTime dayStart = zdtNow.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = zdtNow.toLocalDate().atTime(LocalTime.MAX);

        // 0. Base Filter Setup
        Set<Long> finalUserIds = (allowedUsers != null && !allowedUsers.isEmpty())
            ? allowedUsers.stream().map(User::getId).collect(Collectors.toSet())
            : new HashSet<>();
        
        final List<Long> userIds = new ArrayList<>(finalUserIds);
        boolean hasFilter = (targetUserId != null || teamId != null);
        String currentViewerRole = (requester.getRole() != null) ? requester.getRole().getName() : "ASSOCIATE";
        
        // 1. Dynamic Status Intelligence
        if (hasFilter && userIds.isEmpty()) {
            return DashboardStatsDTO.builder().build();
        }

        if (!isGlobalAdmin && userIds.isEmpty()) {
            return DashboardStatsDTO.builder()
                    .dailyRevenue(BigDecimal.ZERO).monthlyRevenue(BigDecimal.ZERO).expectedRevenue(BigDecimal.ZERO)
                    .monthlyTarget(BigDecimal.ZERO).targetAchievement(0.0)
                    .build();
        }

        final Long requesterId = requester.getId();
        final int currentMonth = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).getMonthValue();
        final int currentYear = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).getYear();

        // 1. Dynamic Status Intelligence
        List<String> rawSuccess = pipelineStageRepository.findByAnalyticBucketIn(List.of("SUCCESS", "CONVERTED", "PAID"))
                .stream().map(s -> s.getStatusValue().toUpperCase()).collect(Collectors.toList());
        final List<String> successStatuses = rawSuccess.isEmpty() 
                ? List.of("CONVERTED", "PAID", "EMI", "SUCCESS", "CLOSED") 
                : rawSuccess;

        List<String> rawLost = pipelineStageRepository.findByAnalyticBucketIn(List.of("LOST", "NOT_INTERESTED", "REJECTED"))
                .stream().map(s -> s.getStatusValue().toUpperCase()).collect(Collectors.toList());
        final List<String> lostStatuses = new ArrayList<>(rawLost);
        if (lostStatuses.isEmpty()) {
            lostStatuses.addAll(List.of("LOST", "NOT_INTERESTED", "REJECTED"));
        }

        List<String> rawInterested = pipelineStageRepository.findByAnalyticBucketIn(List.of("INTERESTED", "UNDER_REVIEW", "FOLLOWUP", "WORKING"))
                .stream().map(s -> s.getStatusValue().toUpperCase()).collect(Collectors.toList());
        final List<String> interestedStatuses = rawInterested.isEmpty()
                ? List.of("INTERESTED", "UNDER_REVIEW", "FOLLOWUP", "WORKING")
                : rawInterested;

        List<String> rawClosed = pipelineStageRepository.findByAnalyticBucketIn(List.of("CLOSED", "COMPLETED", "TERMINATED"))
                .stream().map(s -> s.getStatusValue().toUpperCase()).collect(Collectors.toList());
        final List<String> closedStatuses = rawClosed;
        
        final List<String> terminalStatuses = new ArrayList<>();
        terminalStatuses.addAll(lostStatuses);
        terminalStatuses.add("PAID");
        terminalStatuses.add("CONVERTED");
        terminalStatuses.add("CLOSED");
        
        // excludeStatuses for general pipeline tasks (Follow-ups for non-converted leads)
        final List<String> excludeStatuses = new ArrayList<>(terminalStatuses);
        if (!excludeStatuses.contains("EMI")) excludeStatuses.add("EMI"); // Don't show standard follow-ups for EMI leads

        final List<String> dbStatuses = pipelineStageRepository.findByActiveTrueOrderByOrderIndexAsc()
                .stream().map(PipelineStage::getStatusValue).collect(Collectors.toList());
        final List<String> activeStatuses = dbStatuses.isEmpty() ? List.of("NEW", "CONTACTED", "FOLLOW_UP") : dbStatuses;

        CompletableFuture<Long> activeLoadFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? leadRepository.countByCreatedAtBetween(start, end) 
                         : leadRepository.countByAssignedToIdInAndStatusInAndCreatedAtBetween(userIds, activeStatuses, start.minusMonths(12), end));

        CompletableFuture<long[]> attendanceStatsFuture = CompletableFuture.supplyAsync(() -> {
            long present = 0, halfDay = 0, absent = 0;
            LocalDate statDate = dayStart.toLocalDate();
            
            List<User> usersToCheck = (isGlobalAdmin && !hasFilter) ? userRepository.findAll() : new ArrayList<>(allowedUsers);
            
            for (User u : usersToCheck) {
                if (u.getJoiningDate() != null && statDate.isBefore(u.getJoiningDate())) {
                    continue; 
                }
                
                Optional<AttendanceDaily> d = attendanceDailyRepository.findByUserIdAndDate(u.getId(), statDate);
                if (d.isPresent()) {
                    String status = d.get().getStatus();
                    if ("PRESENT".equals(status)) present++;
                    else if ("HALF_DAY".equals(status)) halfDay++;
                    else absent++;
                } else {
                    absent++;
                }
            }
            return new long[]{present, halfDay, absent};
        });

        // 2. Revenue Block
        CompletableFuture<BigDecimal> dailyRevenueFuture = CompletableFuture.supplyAsync(() -> {
            if (isGlobalAdmin && !hasFilter) return paymentRepository.getGlobalTotalRevenue(start, end);
            if (userIds.isEmpty()) return BigDecimal.ZERO;
            return paymentRepository.getTotalRevenueIn(userIds, start, end);
        });

        CompletableFuture<BigDecimal> monthlyRevenueFuture = CompletableFuture.supplyAsync(() -> {
            if (isGlobalAdmin && !hasFilter) return paymentRepository.getGlobalTotalRevenue(start, end);
            if (userIds.isEmpty()) return BigDecimal.ZERO;
            return paymentRepository.getTotalRevenueIn(userIds, start, end);
        });

        CompletableFuture<BigDecimal> pendingRevenueFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? paymentRepository.getGlobalTotalPendingRevenue()
                         : (userIds.isEmpty() ? BigDecimal.ZERO : paymentRepository.getTotalPendingRevenueByUserIds(userIds)));

        CompletableFuture<BigDecimal> forecastRevenueFuture = CompletableFuture.supplyAsync(() -> 
            (isGlobalAdmin || userIds.isEmpty()) ? BigDecimal.ZERO 
                                                     : paymentRepository.getForecastRevenue(userIds, end, end.plusDays(30)));

        CompletableFuture<Long> pendingPaymentsCountFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? paymentRepository.countGlobalAllPending()
                         : (userIds.isEmpty() ? 0L : paymentRepository.countAllPendingByUserIds(userIds)));
                         
        CompletableFuture<Long> overduePaymentsCountFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? paymentRepository.countGlobalPendingPayments(now)
                         : (userIds.isEmpty() ? 0L : paymentRepository.countPendingPayments(userIds, now)));

        CompletableFuture<Long> todayPaymentsCountFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? taskRepository.countGlobalFollowupsByType("EMI_COLLECTION", dayStart, dayEnd)
                         : (userIds.isEmpty() ? 0L : taskRepository.countFollowupsByType(userIds, "EMI_COLLECTION", dayStart, dayEnd)));

        CompletableFuture<Long> todayFollowupsFuture = CompletableFuture.supplyAsync(() -> {
            long count = isGlobalAdmin ? taskRepository.countGlobalFollowups(dayStart, dayEnd)
                         : (userIds.isEmpty() ? 0L : taskRepository.countFollowups(userIds, dayStart, dayEnd));
            return count;
        });

        CompletableFuture<Long> pendingTasksFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? taskRepository.countGlobalPendingTasks(now)
                                : (userIds.isEmpty() ? 0L : taskRepository.countPendingTasks(userIds, now)));

        CompletableFuture<Long> highPriorityFollowupsFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? leadRepository.countGlobalHighPriorityLeads(now)
                         : (userIds.isEmpty() ? 0L : leadRepository.countHighPriorityLeads(userIds, now)));

        CompletableFuture<Long> completedTodayFuture = CompletableFuture.supplyAsync(() -> {
            return isGlobalAdmin ? taskRepository.countGlobalCompletedToday(dayStart, dayEnd)
                                : (userIds.isEmpty() ? 0L : taskRepository.countCompletedToday(userIds, dayStart, dayEnd));
        });

        // 4. Leads Block
        CompletableFuture<Long> todayLeadsCountFuture = CompletableFuture.supplyAsync(() -> {
            long count = isGlobalAdmin ? taskRepository.countGlobalFollowupsByType("FOLLOW_UP", dayStart, dayEnd)
                         : (userIds.isEmpty() ? 0L : taskRepository.countFollowupsByType(userIds, "FOLLOW_UP", dayStart, dayEnd));
            return count;
        });

        // 5. Tickets Block
        CompletableFuture<Long> activeTicketsFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS))
                         : ticketRepository.countByUsersAndStatusIn(allowedUsers, List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)));

        CompletableFuture<Long> pendingTicketsFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.OPEN))
                         : ticketRepository.countByUsersAndStatusIn(allowedUsers, List.of(TicketStatus.OPEN)));

        CompletableFuture<Long> resolvedTicketsFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.RESOLVED))
                         : ticketRepository.countByUsersAndStatusIn(allowedUsers, List.of(TicketStatus.RESOLVED)));

        CompletableFuture<Long> closedTicketsFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.CLOSED))
                         : ticketRepository.countByUsersAndStatusIn(allowedUsers, List.of(TicketStatus.CLOSED)));

        // 6. User Breakdown Block (Sequential as it's quick)
        Map<String, Long> userBreakdown = new HashMap<>();
        if (isGlobalAdmin) {
            userBreakdown = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null)
                    .collect(Collectors.groupingBy(u -> u.getRole().getName(), Collectors.counting()));
        } else {
            userBreakdown = allowedUsers.stream()
                    .filter(u -> u.getRole() != null)
                    .collect(Collectors.groupingBy(u -> u.getRole().getName(), Collectors.counting()));
        }

        // 7. Ensure userIds is correctly filtered for the specific target context
        final List<Long> finalQueryUserIds;
        if (targetUserId != null) {
            finalQueryUserIds = List.of(targetUserId);
        } else {
            finalQueryUserIds = userIds;
        }

        CompletableFuture<Long> interestedCountFuture = CompletableFuture.supplyAsync(() -> {
            if (isGlobalAdmin && !hasFilter) return leadRepository.countByCreatedAtBetweenAndStatusIn(start, end, interestedStatuses);
            if (finalQueryUserIds.isEmpty()) return 0L;
            return leadRepository.countSquadLeadsByStatus(finalQueryUserIds, interestedStatuses, start, end);
        });

        CompletableFuture<Long> totalLostCountFuture = CompletableFuture.supplyAsync(() -> {
            if (isGlobalAdmin && !hasFilter) return leadRepository.countByCreatedAtBetweenAndStatusIn(start, end, lostStatuses);
            if (finalQueryUserIds.isEmpty()) return 0L;
            return leadRepository.countSquadLeadsByStatus(finalQueryUserIds, lostStatuses, start, end);
        });

        CompletableFuture<Long> totalLeadsCountFuture = CompletableFuture.supplyAsync(() -> {
            if (isGlobalAdmin && !hasFilter) return leadRepository.count();
            if (finalQueryUserIds.isEmpty()) return 0L;
            return leadRepository.countTotalRegistry(finalQueryUserIds);
        });

        CompletableFuture<Long> convertedCountFuture = CompletableFuture.supplyAsync(() -> {
            if (isGlobalAdmin && !hasFilter) return leadRepository.countByStatusIn(successStatuses);
            if (finalQueryUserIds.isEmpty()) return 0L;
            return leadRepository.countSquadConversionsInPeriod(finalQueryUserIds, successStatuses, start, end);
        });

        // 5. Trends Block (New)
        CompletableFuture<List<Map<String, Object>>> leadTrendFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin && !hasFilter ? leadRepository.getGlobalDailyLeadTrend(start, end)
                         : (finalQueryUserIds.isEmpty() ? new ArrayList<>() : leadRepository.getDailyLeadTrendByIds(finalQueryUserIds, start, end)));

        CompletableFuture<List<Map<String, Object>>> convertedTrendFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin && !hasFilter ? leadRepository.getGlobalDailyConvertedTrend(successStatuses, start, end)
                         : (finalQueryUserIds.isEmpty() ? new ArrayList<>() : leadRepository.getDailyConvertedTrendByIds(finalQueryUserIds, successStatuses, start, end)));

        CompletableFuture<List<Map<String, Object>>> lostTrendFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin && !hasFilter ? leadRepository.getGlobalDailyLostTrend(lostStatuses, start, end)
                         : (finalQueryUserIds.isEmpty() ? new ArrayList<>() : leadRepository.getDailyLostTrendByIds(finalQueryUserIds, lostStatuses, start, end)));

        CompletableFuture<List<Map<String, Object>>> revenueTrendFuture = CompletableFuture.supplyAsync(() -> 
            isGlobalAdmin && !hasFilter ? paymentRepository.getGlobalDailyRevenueTrend(start, end)
                         : (finalQueryUserIds.isEmpty() ? new ArrayList<>() : paymentRepository.getDailyRevenueTrendByIds(finalQueryUserIds, start, end)));

        // Wait for all to complete with safety
        try {
            CompletableFuture.allOf(
                activeLoadFuture, attendanceStatsFuture,
                monthlyRevenueFuture, dailyRevenueFuture, pendingRevenueFuture,
                forecastRevenueFuture, pendingPaymentsCountFuture, todayFollowupsFuture,
                pendingTasksFuture, interestedCountFuture, totalLostCountFuture,
                totalLeadsCountFuture, convertedCountFuture, todayLeadsCountFuture,
                activeTicketsFuture, pendingTicketsFuture, resolvedTicketsFuture,
                closedTicketsFuture, todayPaymentsCountFuture, overduePaymentsCountFuture,
                leadTrendFuture, convertedTrendFuture, lostTrendFuture, revenueTrendFuture,
                completedTodayFuture
            ).get(10, java.util.concurrent.TimeUnit.SECONDS);

            List<Map<String, Object>> leadsT = safeGet(leadTrendFuture, new ArrayList<>());
            List<Map<String, Object>> convT = safeGet(convertedTrendFuture, new ArrayList<>());
            List<Map<String, Object>> lostT = safeGet(lostTrendFuture, new ArrayList<>());
            List<Map<String, Object>> revT = safeGet(revenueTrendFuture, new ArrayList<>());

        } catch (Exception e) {
            // Quietly handle partial timeouts
        }

        // Status Distribution (Pre-aggregated)
        Map<String, Long> statusDistribution;
        if (isGlobalAdmin && !hasFilter) {
            statusDistribution = leadRepository.getGlobalSummaryStats(start, end);
        } else {
            statusDistribution = userIds.isEmpty() ? new HashMap<>() : leadRepository.getSummaryStats(userIds, start, end);
        }

        Map<String, Long> mappedDistribution = new HashMap<>();
        if (statusDistribution != null) {
            mappedDistribution.put("NEW", asLong(statusDistribution.get("newCount")));
            mappedDistribution.put("CONTACTED", asLong(statusDistribution.get("contactedCount")));
            mappedDistribution.put("INTERESTED", asLong(statusDistribution.get("interestedCount")));
            mappedDistribution.put("FOLLOW_UP", asLong(statusDistribution.get("followUpCount")));
            mappedDistribution.put("CONVERTED", asLong(statusDistribution.get("convertedCount")));
            mappedDistribution.put("LOST", asLong(statusDistribution.get("lostCount")));
            mappedDistribution.put("REJECTED", asLong(statusDistribution.get("rejectedCount")));
        }

        // 8. Aggregate Trend Data
        List<Map<String, Object>> leadsT = safeGet(leadTrendFuture, new ArrayList<>());
        List<Map<String, Object>> convT = safeGet(convertedTrendFuture, new ArrayList<>());
        List<Map<String, Object>> lostT = safeGet(lostTrendFuture, new ArrayList<>());
        List<Map<String, Object>> revT = safeGet(revenueTrendFuture, new ArrayList<>());

        Map<String, Map<String, Object>> trendMap = new TreeMap<>(); // Sorted by date
        
        // Helper to fill the map
        java.util.function.BiConsumer<List<Map<String, Object>>, String> filler = (list, key) -> {
            for (Map<String, Object> item : list) {
                if (item.get("date") == null && item.get("DATE") == null) continue;
                Object dateObj = item.get("date") != null ? item.get("date") : item.get("DATE");
                String date = dateObj.toString();
                
                trendMap.putIfAbsent(date, new HashMap<>());
                trendMap.get(date).put("date", date);
                
                Object val = item.get("count");
                if (val == null) val = item.get("COUNT");
                if (val == null) val = item.get("amount");
                if (val == null) val = item.get("AMOUNT");
                
                trendMap.get(date).put(key, val != null ? val : 0);
            }
        };

        filler.accept(leadsT, "leads");
        filler.accept(convT, "converted");
        filler.accept(lostT, "lost");
        filler.accept(revT, "revenue");

        List<Map<String, Object>> finalTrend = new ArrayList<>(trendMap.values());

        // Collect Results safely
        long[] attStats = safeGet(attendanceStatsFuture, new long[]{0L, 0L, 0L});
        long present = attStats[0];
        long halfDay = attStats[1];
        long absent = attStats[2];
        BigDecimal monthly = safeGet(monthlyRevenueFuture, BigDecimal.ZERO);
        BigDecimal daily = safeGet(dailyRevenueFuture, BigDecimal.ZERO);
        BigDecimal pendingPaymentsAmount = safeGet(pendingRevenueFuture, BigDecimal.ZERO);
        BigDecimal forecastRevenue = safeGet(forecastRevenueFuture, BigDecimal.ZERO);
        long pendingPayments = safeGet(pendingPaymentsCountFuture, 0L);
        long todayFollowups = safeGet(todayFollowupsFuture, 0L);
        long pendingAppointments = safeGet(pendingTasksFuture, 0L);
        long interestedCount = safeGet(interestedCountFuture, 0L);
        long totalLostCount = safeGet(totalLostCountFuture, 0L);
        long totalLeadsCount = safeGet(totalLeadsCountFuture, 0L);
        long convertedCount = safeGet(convertedCountFuture, 0L);
        long todayLeads = safeGet(todayLeadsCountFuture, 0L);
        long activeTickets = safeGet(activeTicketsFuture, 0L);
        long pendingTickets = safeGet(pendingTicketsFuture, 0L);
        long resolvedTickets = safeGet(resolvedTicketsFuture, 0L);
        long closedTickets = safeGet(closedTicketsFuture, 0L);
        long todayPayments = safeGet(todayPaymentsCountFuture, 0L);
        long overduePayments = safeGet(overduePaymentsCountFuture, 0L);
        long highPriority = safeGet(highPriorityFollowupsFuture, 0L);
        long completedTodayCount = safeGet(completedTodayFuture, 0L);

        // Monthly Target Logic (Sequential as it's quick)
        BigDecimal monthlyTarget = targetRepository
                .findByUserIdAndMonthAndYear(requesterId, currentMonth, currentYear)
                .map(RevenueTarget::getTargetAmount)
                .orElse(requester.getMonthlyTarget());

        if (monthlyTarget == null || monthlyTarget.compareTo(BigDecimal.ZERO) == 0) {
            try {
                GlobalTarget gt = attendanceService.getGlobalTarget();
                if (gt != null) monthlyTarget = gt.getMonthlyRevenueGoal();
            } catch (Exception e) {}
        }
        if (monthlyTarget == null) monthlyTarget = BigDecimal.ZERO;

        BigDecimal expected = monthlyTarget.subtract(monthly).max(BigDecimal.ZERO);
        Double achievement = (monthlyTarget.compareTo(BigDecimal.ZERO) > 0) 
            ? monthly.divide(monthlyTarget, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal(100)).doubleValue()
            : 0.0;

        long totalActiveUsers = isGlobalAdmin 
            ? userRepository.countActiveUsersByDate(to) 
            : userRepository.countActiveUsersByDateIn(finalUserIds, to);

        // 8. Member Performance (REMOVED)
        List<MemberPerformanceDTO> perfStats = new java.util.ArrayList<>();

        return DashboardStatsDTO.builder()
                .presentCount(present)
                .absentCount(absent)
                .halfDayCount(halfDay)
                .dailyRevenue(daily)
                .monthlyRevenue(monthly)
                .expectedRevenue(expected)
                .pendingPaymentsAmount(pendingPaymentsAmount)
                .forecastRevenue(forecastRevenue)
                .todayFollowups(todayFollowups)
                .pendingFollowups(pendingAppointments) // Strictly Tasks for "Today's Schedule" alignment
                .pendingAppointments(pendingAppointments)
                .pendingPayments(pendingPayments)
                .monthlyTarget(monthlyTarget)
                .targetAchievement(achievement)
                .totalLostCount(totalLostCount)
                .interestedCount(interestedCount)
                .interestedToday(0) 
                .totalLeads(totalLeadsCount)
                .convertedCount(convertedCount)
                .totalUsers(totalActiveUsers)
                .userBreakdown(userBreakdown)
                .todayLeadsCount(todayLeads)
                .todayPaymentsCount(todayPayments)
                .completedToday(completedTodayCount)
                .highPriorityFollowups(highPriority)
                .activeSupportTickets(activeTickets)
                .pendingSupportTickets(pendingTickets)
                .resolvedSupportTickets(resolvedTickets)
                .closedSupportTickets(closedTickets)
                .totalPendingCount(pendingPayments)
                .pendingLeadsCount(totalLeadsCount - convertedCount) 
                .overduePaymentsCount(overduePayments) 
                .pendingRevenueAmount(pendingPaymentsAmount)
                .statusDistribution(mappedDistribution)
                .performance(perfStats)
                .dailyTrend(finalTrend)
                .build();
    }

    private List<User> getTargetUsers(User user) {
        if (user.getRole() != null && user.getRole().getName().equals("ADMIN")) {
            return userRepository.findAll();
        }

        List<Long> ids = userRepository.findSubordinateIds(user.getId());
        List<User> result = new java.util.ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            result.addAll(userRepository.findAllById(ids));
        }
        result.add(user);
        return result;
    }

    private <T> T safeGet(CompletableFuture<T> future, T defaultValue) {
        try {
            if (future == null) return defaultValue;
            T result = future.getNow(defaultValue);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }


}
