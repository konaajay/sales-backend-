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
import java.util.function.Supplier;
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

    @Autowired
    private SecurityService securityService;

    private <T> CompletableFuture<T> safeAsync(Supplier<T> supplier, T fallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = supplier.get();
                return result != null ? result : fallback;
            } catch (Exception e) {
                return fallback;
            }
        });
    }

    public DashboardSummaryDTO getUnifiedSummary(User requester, LocalDate from, LocalDate to, Long targetUserId,
            Long teamId, Long managerId) {
        if (requester == null)
            return null;

        User user = userRepository.findById(requester.getId()).orElse(requester);
        LocalDateTime start = (from != null ? from : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime end = (to != null ? to : LocalDate.now()).atTime(LocalTime.MAX);

        Set<Long> userIds;
        if (targetUserId != null && targetUserId > 0) {
            securityService.validateAccess(user, targetUserId);
            userIds = Set.of(targetUserId);
        } else {
            userIds = securityService.getScopedUserIds(user, managerId, teamId);
        }

        boolean isFiltered = targetUserId != null || teamId != null || managerId != null;
        boolean isGlobalAdmin = securityService.isAdmin(user) && !isFiltered;

        DashboardStatsDTO stats = getStats(userIds, from != null ? from : LocalDate.now().minusDays(30),
                to != null ? to : LocalDate.now(), isGlobalAdmin, user, targetUserId,
                teamId != null ? teamId : managerId);

        ReportFilterDTO filter = ReportFilterDTO.builder()
                .fromDate(from != null ? from : LocalDate.now().minusDays(30))
                .toDate(to != null ? to : LocalDate.now())
                .build();
        if (targetUserId != null)
            filter.setUserId(targetUserId);
        else if (teamId != null)
            filter.setTeamLeaderId(teamId);
        else if (managerId != null)
            filter.setManagerId(managerId);

        List<TimeSeriesStatsDTO> trend = reportService.getFilteredTrend(filter);

        List<DashboardProjection> distributionList = isGlobalAdmin ? leadRepository.countByStatusGlobal(start, end)
                : leadRepository.countByStatusForUsers(userIds, start, end);

        Map<String, Long> mappedDistribution = new HashMap<>();
        for (DashboardProjection p : distributionList) {
            if (p.getStatus() != null)
                mappedDistribution.put(p.getStatus().toUpperCase(), p.getCount());
        }

        mappedDistribution.putIfAbsent("NEW", 0L);
        mappedDistribution.putIfAbsent("CONTACTED", 0L);
        mappedDistribution.putIfAbsent("FOLLOW_UP", mappedDistribution.getOrDefault("FOLLOWUP", 0L));
        mappedDistribution.putIfAbsent("CONVERTED",
                mappedDistribution.getOrDefault("PAID", 0L) + mappedDistribution.getOrDefault("SUCCESS", 0L));

        return DashboardSummaryDTO.builder().stats(stats).trend(trend).statusDistribution(mappedDistribution)
                .performance(stats.getPerformance()).build();
    }

    public DashboardStatsDTO getStats(Collection<Long> userIds, LocalDate from, LocalDate to, boolean isGlobalAdmin,
            User requester, Long targetUserId, Long teamId) {
        if (requester == null)
            return null;

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime zdtNow = ZonedDateTime.now(zone);
        LocalDateTime now = zdtNow.toLocalDateTime();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);
        LocalDateTime dayStart = zdtNow.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = zdtNow.toLocalDate().atTime(LocalTime.MAX);

        List<User> activeScopeUsers = userRepository.findAllById(userIds).stream()
                .filter(u -> u.getJoiningDate() == null || !u.getJoiningDate().isAfter(to))
                .collect(Collectors.toList());

        final List<Long> userIdList = activeScopeUsers.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        java.util.Map<String, Long> userBreakdown = activeScopeUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRole() != null ? u.getRole().getName().replace("ROLE_", "") : "UNKNOWN",
                        Collectors.counting()));

        if ((targetUserId != null || teamId != null) && userIdList.isEmpty())
            return DashboardStatsDTO.builder().build();
        if (!isGlobalAdmin && userIdList.isEmpty()) {
            return DashboardStatsDTO.builder().dailyRevenue(BigDecimal.ZERO).monthlyRevenue(BigDecimal.ZERO)
                    .expectedRevenue(BigDecimal.ZERO).monthlyTarget(BigDecimal.ZERO).targetAchievement(0.0).build();
        }

        List<String> dbSuccess = pipelineStageRepository.findByAnalyticBucketIn(List.of("SUCCESS", "CONVERTED", "PAID"))
                .stream().map(PipelineStage::getStatusValue).collect(Collectors.toList());
        final List<String> successStatuses = dbSuccess.isEmpty() ? List.of("CONVERTED", "PAID", "SUCCESS") : dbSuccess;

        List<String> dbLost = pipelineStageRepository
                .findByAnalyticBucketIn(List.of("LOST", "NOT_INTERESTED", "REJECTED")).stream()
                .map(PipelineStage::getStatusValue).collect(Collectors.toList());
        final List<String> lostStatuses = dbLost.isEmpty() ? List.of("LOST", "NOT_INTERESTED", "REJECTED") : dbLost;

        List<String> dbInterested = pipelineStageRepository
                .findByAnalyticBucketIn(List.of("INTERESTED", "UNDER_REVIEW", "FOLLOWUP", "WORKING")).stream()
                .map(PipelineStage::getStatusValue).collect(Collectors.toList());
        final List<String> interestedStatuses = dbInterested.isEmpty() ? List.of("INTERESTED", "UNDER_REVIEW", "FOLLOW_UP", "WORKING") : dbInterested;

        List<String> dbActive = pipelineStageRepository.findByActiveTrueOrderByOrderIndexAsc().stream()
                .map(PipelineStage::getStatusValue).collect(Collectors.toList());
        final List<String> activeStatuses = dbActive.isEmpty() ? List.of("NEW", "CONTACTED", "FOLLOW_UP") : dbActive;

        CompletableFuture<Long> activeLoadFuture = safeAsync(
                () -> isGlobalAdmin ? leadRepository.countByCreatedAtBetween(start, end)
                        : leadRepository.countByAssignedToIdInAndStatusInAndCreatedAtBetween(userIdList, activeStatuses,
                                start.minusMonths(12), end),
                0L);

        // Optimized Attendance Stats using Repository Counts
        CompletableFuture<Long> presentCountFuture = safeAsync(
                () -> isGlobalAdmin ? attendanceRepository.countPresentUsers(dayStart, now)
                        : attendanceRepository.countPresentUsersIn(userIdList, dayStart, now),
                0L);
        CompletableFuture<Long> lateCountFuture = safeAsync(
                () -> isGlobalAdmin ? attendanceRepository.countLateUsers(dayStart, now)
                        : attendanceRepository.countLateUsersIn(userIdList, dayStart, now),
                0L);

        CompletableFuture<BigDecimal> dailyRevenueFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.getGlobalTotalRevenue(start, end)
                        : paymentRepository.getTotalRevenueIn(userIdList, start, end),
                BigDecimal.ZERO);
        CompletableFuture<BigDecimal> monthlyRevenueFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.getGlobalTotalRevenue(start.withDayOfMonth(1), end)
                        : paymentRepository.getTotalRevenueIn(userIdList, start.withDayOfMonth(1), end),
                BigDecimal.ZERO);
        CompletableFuture<BigDecimal> pendingRevenueFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.getGlobalTotalPendingRevenueIn(start, end)
                        : paymentRepository.getTotalPendingRevenueIn(userIdList, start, end),
                BigDecimal.ZERO);
        CompletableFuture<BigDecimal> forecastRevenueFuture = safeAsync(
                () -> (isGlobalAdmin || userIdList.isEmpty()) ? BigDecimal.ZERO
                        : paymentRepository.getForecastRevenue(userIdList, end, end.plusDays(30)),
                BigDecimal.ZERO);

        CompletableFuture<Long> pendingPaymentsCountFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalPendingTasksByType("EMI_COLLECTION", now)
                        : taskRepository.countPendingTasksByType(userIdList, "EMI_COLLECTION", now),
                0L);
        CompletableFuture<Long> pendingLeadsCountFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalPendingTasksByType("FOLLOW_UP", now)
                        : taskRepository.countPendingTasksByType(userIdList, "FOLLOW_UP", now),
                0L);
        CompletableFuture<Long> overduePaymentsCountFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.countGlobalPendingPayments(now)
                        : paymentRepository.countPendingPayments(userIdList, now),
                0L);
        CompletableFuture<Long> todayPaymentsCountFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalFollowupsByType("EMI_COLLECTION", dayStart, dayEnd)
                        : taskRepository.countFollowupsByType(userIdList, "EMI_COLLECTION", dayStart, dayEnd),
                0L);
        CompletableFuture<Long> todayFollowupsFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalFollowups(dayStart, dayEnd)
                        : taskRepository.countFollowups(userIdList, dayStart, dayEnd),
                0L);
        CompletableFuture<Long> pendingTasksFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalPendingTasks(now)
                        : taskRepository.countPendingTasks(userIdList, now),
                0L);
        CompletableFuture<Long> highPriorityFollowupsFuture = safeAsync(
                () -> isGlobalAdmin ? leadRepository.countGlobalHighPriorityLeads(now)
                        : leadRepository.countHighPriorityLeads(userIdList, now),
                0L);
        CompletableFuture<Long> completedTodayFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalCompletedToday(dayStart, dayEnd)
                        : taskRepository.countCompletedToday(userIdList, dayStart, dayEnd),
                0L);

        CompletableFuture<Long> activeTicketsFuture = safeAsync(() -> isGlobalAdmin
                ? ticketRepository.countByStatusIn(List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS))
                : ticketRepository.countByUserIdInAndStatusIn(userIdList,
                        List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)),
                0L);
        CompletableFuture<Long> pendingTicketsFuture = safeAsync(
                () -> isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.OPEN))
                        : ticketRepository.countByUserIdInAndStatusIn(userIdList, List.of(TicketStatus.OPEN)),
                0L);
        CompletableFuture<Long> resolvedTicketsFuture = safeAsync(
                () -> isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.RESOLVED))
                        : ticketRepository.countByUserIdInAndStatusIn(userIdList, List.of(TicketStatus.RESOLVED)),
                0L);
        CompletableFuture<Long> closedTicketsFuture = safeAsync(
                () -> isGlobalAdmin ? ticketRepository.countByStatusIn(List.of(TicketStatus.CLOSED))
                        : ticketRepository.countByUserIdInAndStatusIn(userIdList, List.of(TicketStatus.CLOSED)),
                0L);

        final List<Long> finalQueryUserIds = (targetUserId != null) ? List.of(targetUserId) : userIdList;
        final boolean noFilters = targetUserId == null && teamId == null;

        CompletableFuture<Long> interestedCountFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? leadRepository.countByCreatedAtBetweenAndStatusIn(start, end, interestedStatuses)
                        : leadRepository.countSquadLeadsByStatus(finalQueryUserIds, interestedStatuses, start, end),
                0L);
        CompletableFuture<Long> totalLostCountFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? leadRepository.countByCreatedAtBetweenAndStatusIn(start, end, lostStatuses)
                        : leadRepository.countSquadLeadsByStatus(finalQueryUserIds, lostStatuses, start, end),
                0L);
        CompletableFuture<Long> totalLeadsCountFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters) ? leadRepository.count()
                        : leadRepository.countTotalRegistry(finalQueryUserIds),
                0L);
        CompletableFuture<Long> convertedCountFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? leadRepository.countByStatusIn(successStatuses)
                        : leadRepository.countSquadConversionsInPeriod(finalQueryUserIds, successStatuses, start, end),
                0L);

        CompletableFuture<List<Map<String, Object>>> leadTrendFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? leadRepository.getGlobalDailyLeadTrend(start, end)
                        : leadRepository.getDailyLeadTrendByIds(finalQueryUserIds, start, end),
                new ArrayList<>());
        CompletableFuture<List<Map<String, Object>>> convertedTrendFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? leadRepository.getGlobalDailyConvertedTrend(successStatuses, start, end)
                        : leadRepository.getDailyConvertedTrendByIds(finalQueryUserIds, successStatuses, start, end),
                new ArrayList<>());
        CompletableFuture<List<Map<String, Object>>> lostTrendFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? leadRepository.getGlobalDailyLostTrend(lostStatuses, start, end)
                        : leadRepository.getDailyLostTrendByIds(finalQueryUserIds, lostStatuses, start, end),
                new ArrayList<>());
        CompletableFuture<List<Map<String, Object>>> revenueTrendFuture = safeAsync(
                () -> (isGlobalAdmin && noFilters)
                        ? paymentRepository.getGlobalDailyRevenueTrend(start, end)
                        : paymentRepository.getDailyRevenueTrendByIds(finalQueryUserIds, start, end),
                new ArrayList<>());

        try {
            CompletableFuture.allOf(activeLoadFuture, monthlyRevenueFuture, dailyRevenueFuture,
                    pendingRevenueFuture, forecastRevenueFuture, pendingPaymentsCountFuture, todayFollowupsFuture,
                    pendingTasksFuture, interestedCountFuture, totalLostCountFuture, totalLeadsCountFuture,
                    convertedCountFuture, activeTicketsFuture, pendingTicketsFuture, resolvedTicketsFuture,
                    closedTicketsFuture, todayPaymentsCountFuture, overduePaymentsCountFuture, pendingLeadsCountFuture,
                    leadTrendFuture, convertedTrendFuture, lostTrendFuture, revenueTrendFuture, completedTodayFuture,
                    presentCountFuture, lateCountFuture)
                    .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
        }

        Map<String, Long> statusDistribution = (isGlobalAdmin && noFilters)
                ? leadRepository.getGlobalSummaryStats(start, end)
                : leadRepository.getSummaryStats(userIdList, start, end);
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

        Map<String, Map<String, Object>> trendMap = new TreeMap<>();
        java.util.function.BiConsumer<List<Map<String, Object>>, String> filler = (list, key) -> {
            for (Map<String, Object> item : list) {
                Object dateObj = item.get("date") != null ? item.get("date") : item.get("DATE");
                if (dateObj == null)
                    continue;
                String date = dateObj.toString();
                trendMap.putIfAbsent(date, new HashMap<>());
                trendMap.get(date).put("date", date);
                Object val = item.get("count");
                if (val == null)
                    val = item.get("COUNT");
                if (val == null)
                    val = item.get("amount");
                if (val == null)
                    val = item.get("AMOUNT");
                trendMap.get(date).put(key, val != null ? val : 0);
            }
        };
        filler.accept(leadTrendFuture.join(), "leads");
        filler.accept(convertedTrendFuture.join(), "converted");
        filler.accept(lostTrendFuture.join(), "lost");
        filler.accept(revenueTrendFuture.join(), "revenue");

        // long[] attStats = attendanceStatsFuture.join(); // Removed as we run synchronously now
        BigDecimal monthly = monthlyRevenueFuture.join();
        // Use month/year from the filter range end instead of start to ensure current month targets show up in 'Last 30 Days' views
        int filterMonth = (to != null ? to : LocalDate.now()).getMonthValue();
        int filterYear = (to != null ? to : LocalDate.now()).getYear();

        // 1. Context-Aware Assigned Target (Top Number)
        Long assignedSubjectId = (targetUserId != null && targetUserId > 0) ? targetUserId : requester.getId();
        
        // Final broad search for any target record for this user in this month/year
        BigDecimal monthlyTarget = BigDecimal.ZERO;
        List<RevenueTarget> targets = targetRepository.findAllByUserIdAndMonthAndYearOrderByIdDesc(assignedSubjectId, filterMonth, filterYear);
        
        if (!targets.isEmpty()) {
            // Prioritize the target they assigned to themselves (their personal goal in a distribution)
            // If not found, pick the latest budget assigned to them by a supervisor
            monthlyTarget = targets.stream()
                .filter(t -> t.getAssignedBy().equals(assignedSubjectId))
                .findFirst()
                .map(RevenueTarget::getTargetAmount)
                .orElse(targets.get(0).getTargetAmount());
        }

        // Final safety net to prevent NullPointerException in calculations below
        if (monthlyTarget == null) {
            monthlyTarget = BigDecimal.ZERO;
        }

        // 2. Context-Aware Distributed Target (Bottom Number)
        BigDecimal distributedTarget = BigDecimal.ZERO;
        
        Long distributionSubjectId = (targetUserId != null && targetUserId > 0) ? targetUserId : requester.getId();
        if (teamId != null && teamId > 0 && (targetUserId == null || targetUserId <= 0)) {
            distributionSubjectId = teamId;
        }

        // Fetch all targets assigned BY this subject to their subordinates
        List<RevenueTarget> assignedBySubject = targetRepository.findAllByAssignedByAndMonthAndYear(distributionSubjectId, filterMonth, filterYear);
        
        if (assignedBySubject != null && !assignedBySubject.isEmpty()) {
            Map<Long, Long> latestIdMap = new HashMap<>();
            Map<Long, BigDecimal> targetMap = new HashMap<>();
            
            for (RevenueTarget rt : assignedBySubject) {
                Long uid = rt.getUser().getId();
                // Ensure we only sum the latest version of a target for each subordinate
                if (!latestIdMap.containsKey(uid) || rt.getId() > latestIdMap.get(uid)) {
                    latestIdMap.put(uid, rt.getId());
                    targetMap.put(uid, rt.getTargetAmount());
                }
            }
            distributedTarget = targetMap.values().stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // If filtering is active OR we are in a Global Admin view,
        // we calculate the aggregate target for the entire visible scope.
        if (targetUserId != null || teamId != null || isGlobalAdmin) {
            BigDecimal aggregateTarget = targetRepository.findTotalTargetForUsers(userIdList, filterMonth, filterYear);
            if (aggregateTarget != null && aggregateTarget.compareTo(BigDecimal.ZERO) > 0) {
                monthlyTarget = aggregateTarget;
            }
        }

        Double achievement = (monthlyTarget != null && monthlyTarget.compareTo(BigDecimal.ZERO) > 0) ? monthly
                .divide(monthlyTarget, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal(100)).doubleValue()
                : 0.0;

        Long presentCount = presentCountFuture.join();
        Long lateCount = lateCountFuture.join();
        Long absentCount = Math.max(0L, (long)userIdList.size() - presentCount);

        return DashboardStatsDTO.builder()
                .presentCount(presentCount).absentCount(absentCount).halfDayCount(0L)
                .lateCount(lateCount)
                .dailyRevenue(dailyRevenueFuture.join()).monthlyRevenue(monthly)
                .expectedRevenue(pendingRevenueFuture.join())
                .pendingPaymentsAmount(pendingRevenueFuture.join()).forecastRevenue(forecastRevenueFuture.join())
                .todayFollowups(todayFollowupsFuture.join()).pendingFollowups(pendingTasksFuture.join())
                .pendingAppointments(pendingTasksFuture.join())
                .pendingPayments(pendingPaymentsCountFuture.join()).monthlyTarget(monthlyTarget)
                .distributedTarget(distributedTarget)
                .targetAchievement(achievement)
                .totalLostCount(totalLostCountFuture.join()).interestedCount(interestedCountFuture.join())
                .totalLeads(totalLeadsCountFuture.join()).convertedCount(convertedCountFuture.join())
                .totalUsers((long) userIdList.size()).todayLeadsCount(todayFollowupsFuture.join())
                .todayPaymentsCount(todayPaymentsCountFuture.join()).completedToday(completedTodayFuture.join())
                .highPriorityFollowups(highPriorityFollowupsFuture.join())
                .activeSupportTickets(activeTicketsFuture.join()).pendingSupportTickets(pendingTicketsFuture.join())
                .resolvedSupportTickets(resolvedTicketsFuture.join()).closedSupportTickets(closedTicketsFuture.join())
                .totalPendingCount(pendingPaymentsCountFuture.join()).pendingLeadsCount(pendingLeadsCountFuture.join())
                .pendingPaymentsCount(pendingPaymentsCountFuture.join())
                .overduePaymentsCount(overduePaymentsCountFuture.join())
                .pendingRevenueAmount(pendingRevenueFuture.join())
                .statusDistribution(mappedDistribution).userBreakdown(userBreakdown)
                .dailyTrend(new ArrayList<>(trendMap.values())).build();
    }

    public Collection<User> determineAllowedUsers(User requester, Long userId, Long teamId) {
        Set<Long> ids = securityService.getScopedUserIds(requester, null, teamId);
        if (userId != null) {
            securityService.validateAccess(requester, userId);
            ids = Set.of(userId);
        }
        return userRepository.findAllById(ids);
    }

    public List<Map<String, Object>> getMemberPerformanceFiltered(LocalDateTime start, LocalDateTime end,
            User requester, Long userId, Long tlId, Long managerId) {
        Set<Long> userIds = securityService.getScopedUserIds(requester, managerId, tlId);
        if (userId != null) {
            securityService.validateAccess(requester, userId);
            userIds = Set.of(userId);
        }

        List<Map<String, Object>> revenueData = paymentRepository.getRevenuePerUser(userIds, start, end);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> data : revenueData) {
            Long uid = (Long) data.get("userId");
            userRepository.findById(uid).ifPresent(u -> {
                Map<String, Object> row = new HashMap<>(data);
                row.put("userName", u.getName());
                row.put("userEmail", u.getEmail());
                result.add(row);
            });
        }
        return result;
    }

    public Map<String, Long> getGlobalStats() {
        LocalDateTime start = LocalDateTime.now().minusYears(1);
        LocalDateTime end = LocalDateTime.now();
        List<DashboardProjection> distributionList = leadRepository.countByStatusGlobal(start, end);
        Map<String, Long> mappedDistribution = new HashMap<>();
        for (DashboardProjection p : distributionList) {
            if (p.getStatus() != null)
                mappedDistribution.put(p.getStatus().toUpperCase(), p.getCount());
        }
        return mappedDistribution;
    }

    private long asLong(Object val) {
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }
}
