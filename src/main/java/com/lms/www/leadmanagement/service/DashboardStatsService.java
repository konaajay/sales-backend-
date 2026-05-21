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

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
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
            userIds = new HashSet<>(Set.of(targetUserId));
        } else {
            userIds = new HashSet<>(securityService.getScopedUserIds(user, managerId, teamId));
            // Only add self (Admin/Manager) to the count if we are viewing the global/default scope
            boolean isFiltered = managerId != null || teamId != null;
            if (!isFiltered) {
                userIds.add(user.getId());
            }
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

        mappedDistribution.putIfAbsent("OPEN", 0L);
        mappedDistribution.putIfAbsent("CONTACTED", 0L);
        mappedDistribution.putIfAbsent("FOLLOW_UP", mappedDistribution.getOrDefault("FOLLOWUP", 0L) + mappedDistribution.getOrDefault("EMI_FOLLOWUP", 0L));
        mappedDistribution.putIfAbsent("CONVERTED",
                mappedDistribution.getOrDefault("PAID", 0L) + mappedDistribution.getOrDefault("SUCCESS", 0L));
        mappedDistribution.put("DNP",
                mappedDistribution.getOrDefault("DNP", 0L) +
                mappedDistribution.getOrDefault("SWITCH_OFF", 0L) +
                mappedDistribution.getOrDefault("SWITCHED_OFF", 0L) +
                mappedDistribution.getOrDefault("OUT_OF_COVERAGE", 0L) +
                mappedDistribution.getOrDefault("OUT_OF_COVERAGE_AREA", 0L) +
                mappedDistribution.getOrDefault("WRONG_NUMBER", 0L) +
                mappedDistribution.getOrDefault("NOT_RESPONDING", 0L));

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

        
        List<User> activeScopeUsers = userRepository.findAllById(new ArrayList<>(userIds)).stream()
                .filter(u -> {
                    boolean joinDateOk = u.getJoiningDate() == null || !u.getJoiningDate().isAfter(to);
                    boolean isActive = u.isActive();
                    // If we are in a filtered view, exclude ADMINs from the staff count
                    if (teamId != null) {
                        String r = u.getRole() != null ? u.getRole().getName().toUpperCase() : "";
                        return !r.contains("ADMIN") && joinDateOk && isActive;
                    }
                    return joinDateOk && isActive;
                })
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
        final List<String> successStatuses = new ArrayList<>();
        if (dbSuccess.isEmpty()) {
            successStatuses.addAll(List.of("CONVERTED", "PAID", "SUCCESS"));
        } else {
            successStatuses.addAll(dbSuccess);
        }
        successStatuses.addAll(List.of("EMI", "PRE_PAYMENT", "PRE-PAYMENT"));
        for (int i = 1; i <= 25; i++) {
            successStatuses.add("PAID_INSTALLMENT_" + i);
        }


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
        final List<String> activeStatuses = dbActive.isEmpty() ? List.of("OPEN", "CONTACTED", "FOLLOW_UP") : dbActive;

        CompletableFuture<Long> activeLoadFuture = safeAsync(
                () -> isGlobalAdmin ? leadRepository.countByCreatedAtBetween(start, end)
                        : leadRepository.countByAssignedToIdInAndStatusInAndCreatedAtBetween(userIdList, activeStatuses,
                                start.minusMonths(12), end),
                0L);

        // Optimized Attendance Stats using Range-Aware Daily Logs
        List<AttendanceDaily> rangeLogs = attendanceDailyRepository.findAllByUserIdInAndDateBetween(userIdList, from, to);
        
        // Calculate global present/late/absent by summing up individual user stats
        // (will be calculated inside the performance loop to ensure consistency)
        long totalPresentCount = 0;
        long totalLateCount = 0;
        long totalAbsentCount = 0;

        // If today and range is only today, reconcile with real-time capacity
        if (from.equals(to) && from.equals(LocalDate.now())) {
            // Optional: fallback to real-time session repository if needed, 
            // but rangeLogs from AttendanceDaily (which is updated on every punch) is usually sufficient.
        }

        CompletableFuture<BigDecimal> dailyRevenueFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.getGlobalTotalRevenue(start, end)
                                : paymentRepository.getTotalRevenueIn(userIdList, start, end),
                BigDecimal.ZERO);
        CompletableFuture<BigDecimal> monthlyRevenueFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.getGlobalTotalRevenue(start.withDayOfMonth(1), end)
                                : paymentRepository.getTotalRevenueIn(userIdList, start.withDayOfMonth(1), end),
                BigDecimal.ZERO);
        CompletableFuture<BigDecimal> pendingRevenueFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.getGlobalTotalPendingRevenueIn(start.minusYears(5))
                                : paymentRepository.getTotalPendingRevenueIn(userIdList, start.minusYears(5)),
                BigDecimal.ZERO);
        CompletableFuture<BigDecimal> forecastRevenueFuture = safeAsync(
                () -> (isGlobalAdmin || userIdList.isEmpty()) ? BigDecimal.ZERO
                        : paymentRepository.getForecastRevenue(userIdList, end, end.plusDays(30)),
                BigDecimal.ZERO);

        CompletableFuture<Long> pendingPaymentsCountFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalOverdueTasksByType("EMI_COLLECTION", now)
                        : taskRepository.countOverdueTasksByType(userIdList, "EMI_COLLECTION", now),
                0L);
        CompletableFuture<Long> pendingLeadsCountFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalOverdueTasksByType("FOLLOW_UP", now)
                        : taskRepository.countOverdueTasksByType(userIdList, "FOLLOW_UP", now),
                0L);
        CompletableFuture<Long> overduePaymentsCountFuture = safeAsync(
                () -> isGlobalAdmin ? paymentRepository.countGlobalPendingPayments(now)
                        : paymentRepository.countPendingPayments(userIdList, now),
                0L);
        CompletableFuture<Long> todayPaymentsCountFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalTasksDueTodayByType(now, dayEnd, "EMI_COLLECTION")
                        : taskRepository.countTasksDueTodayByType(userIdList, now, dayEnd, "EMI_COLLECTION"),
                0L);
        CompletableFuture<Long> todayFollowupsFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalTasksDueTodayByType(now, dayEnd, "FOLLOW_UP")
                        : taskRepository.countTasksDueTodayByType(userIdList, now, dayEnd, "FOLLOW_UP"),
                0L);
        CompletableFuture<Long> pendingTasksFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalOverdueTasks(now)
                        : taskRepository.countOverdueTasks(userIdList, now),
                0L);
        CompletableFuture<Long> highPriorityFollowupsFuture = safeAsync(
                () -> isGlobalAdmin ? leadRepository.countGlobalHighPriorityLeads(now)
                        : leadRepository.countHighPriorityLeads(userIdList, now),
                0L);
        CompletableFuture<Long> completedTodayFuture = safeAsync(
                () -> isGlobalAdmin ? taskRepository.countGlobalCompletedToday(start, end)
                        : taskRepository.countCompletedToday(userIdList, start, end),
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
                    leadTrendFuture, convertedTrendFuture, lostTrendFuture, revenueTrendFuture, completedTodayFuture)
                    .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
        }

        Map<String, Long> statusDistribution = (isGlobalAdmin && noFilters)
                ? leadRepository.getGlobalSummaryStats(start, end)
                : leadRepository.getSummaryStats(userIdList, start, end);
        Map<String, Long> mappedDistribution = new HashMap<>();
        if (statusDistribution != null) {
            mappedDistribution.put("OPEN", asLong(statusDistribution.get("newCount")));
            mappedDistribution.put("CONTACTED", asLong(statusDistribution.get("contactedCount")));
            mappedDistribution.put("INTERESTED", asLong(statusDistribution.get("interestedCount")));
            mappedDistribution.put("FOLLOW_UP", asLong(statusDistribution.get("followUpCount")));
            mappedDistribution.put("CONVERTED", asLong(statusDistribution.get("convertedCount")));
            mappedDistribution.put("LOST", asLong(statusDistribution.get("lostCount")));
            mappedDistribution.put("REJECTED", asLong(statusDistribution.get("rejectedCount")));
            mappedDistribution.put("DNP", asLong(statusDistribution.get("dnpCount")));
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

        BigDecimal monthly = monthlyRevenueFuture.join();
        int filterMonth = (to != null ? to : LocalDate.now()).getMonthValue();
        int filterYear = (to != null ? to : LocalDate.now()).getYear();

        Long assignedSubjectId = (targetUserId != null && targetUserId > 0) ? targetUserId : 
                                 (teamId != null && teamId > 0 ? teamId : requester.getId());
        BigDecimal monthlyTarget = BigDecimal.ZERO;
        
        List<RevenueTarget> targets = targetRepository.findAllByUserIdAndMonthAndYearOrderByIdDesc(assignedSubjectId, filterMonth, filterYear);
        if (!targets.isEmpty()) {
            boolean isPersonalHomeView = (targetUserId != null && targetUserId.equals(requester.getId()));
            if (isPersonalHomeView) {
                monthlyTarget = targets.stream()
                    .filter(t -> t.getType() != null && "DISTRIBUTED".equalsIgnoreCase(t.getType().name()) && t.getAssignedBy().equals(assignedSubjectId))
                    .findFirst()
                    .map(RevenueTarget::getTargetAmount)
                    .orElseGet(() -> targets.stream()
                        .filter(t -> t.getType() != null && "ASSIGNED".equalsIgnoreCase(t.getType().name()))
                        .findFirst()
                        .map(RevenueTarget::getTargetAmount)
                        .orElse(targets.get(0).getTargetAmount()));
            } else {
                monthlyTarget = targets.stream()
                    .filter(t -> t.getType() != null && "ASSIGNED".equalsIgnoreCase(t.getType().name()) && !t.getAssignedBy().equals(assignedSubjectId))
                    .findFirst()
                    .map(RevenueTarget::getTargetAmount)
                    .orElseGet(() -> targets.stream()
                        .max(Comparator.comparing(RevenueTarget::getTargetAmount))
                        .map(RevenueTarget::getTargetAmount)
                        .orElse(targets.get(0).getTargetAmount()));
            }
        }
        if (monthlyTarget == null) monthlyTarget = BigDecimal.ZERO;

        BigDecimal distributedTarget = BigDecimal.ZERO;
        Long distributionSubjectId = (targetUserId != null && targetUserId > 0) ? targetUserId : requester.getId();
        if (teamId != null && teamId > 0 && (targetUserId == null || targetUserId <= 0)) {
            distributionSubjectId = teamId;
        }

        List<RevenueTarget> assignedBySubject = targetRepository.findAllByAssignedByAndMonthAndYear(distributionSubjectId, filterMonth, filterYear);
        if (assignedBySubject != null && !assignedBySubject.isEmpty()) {
            Map<Long, Long> latestIdMap = new HashMap<>();
            Map<Long, BigDecimal> targetMap = new HashMap<>();
            for (RevenueTarget rt : assignedBySubject) {
                Long uid = rt.getUser().getId();
                if (!latestIdMap.containsKey(uid) || rt.getId() > latestIdMap.get(uid)) {
                    latestIdMap.put(uid, rt.getId());
                    targetMap.put(uid, rt.getTargetAmount());
                }
            }
            distributedTarget = targetMap.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Double achievement = (monthlyTarget != null && monthlyTarget.compareTo(BigDecimal.ZERO) > 0) ? monthly
                .divide(monthlyTarget, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal(100)).doubleValue()
                : 0.0;

        List<MemberPerformanceDTO> performance = new ArrayList<>();
        if (!userIdList.isEmpty()) {
            List<Map<String, Object>> revenuePerUser = paymentRepository.getRevenuePerUser(new HashSet<>(userIdList), start, end);
            Map<Long, BigDecimal> revenueMap = new HashMap<>();
            if (revenuePerUser != null) {
                for (Map<String, Object> m : revenuePerUser) {
                    Long uid = (Long) m.get("userId");
                    BigDecimal rev = (BigDecimal) m.get("totalRevenue");
                    if (uid != null) revenueMap.put(uid, rev != null ? rev : BigDecimal.ZERO);
                }
            }

            Map<Long, List<AttendanceDaily>> attendanceByUser = rangeLogs.stream().collect(Collectors.groupingBy(a -> a.getUser().getId()));
            for (User u : activeScopeUsers) {
                Long uid = u.getId();
                BigDecimal userTarget = BigDecimal.ZERO;
                List<RevenueTarget> userTargets = targetRepository.findAllByUserIdAndMonthAndYearOrderByIdDesc(uid, filterMonth, filterYear);
                if (!userTargets.isEmpty()) {
                    userTarget = userTargets.stream().filter(t -> t.getType() != null && "ASSIGNED".equalsIgnoreCase(t.getType().name())).findFirst().map(RevenueTarget::getTargetAmount).orElse(userTargets.get(0).getTargetAmount());
                }

                List<AttendanceDaily> userLogs = attendanceByUser.getOrDefault(uid, new ArrayList<>());
                long userPresents = userLogs.stream().filter(a -> a.getStatus() != null && !"ABSENT".equalsIgnoreCase(a.getStatus())).count();
                long userLates = userLogs.stream().filter(AttendanceDaily::isLate).count();
                long userAbsents = 0;
                LocalDate effectiveStart = (u.getJoiningDate() != null && u.getJoiningDate().isAfter(from)) ? u.getJoiningDate() : from;
                LocalDate effectiveEnd = to.isAfter(LocalDate.now()) ? LocalDate.now() : to;
                if (!effectiveStart.isAfter(effectiveEnd)) {
                    long totalExpectedDays = java.time.temporal.ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
                    userAbsents = Math.max(0, totalExpectedDays - userPresents);
                }
                performance.add(MemberPerformanceDTO.builder().userId(uid).username(u.getName()).role(u.getRole() != null ? u.getRole().getName().replace("ROLE_", "") : "USER").targetAmount(userTarget).monthlyRevenue(revenueMap.getOrDefault(uid, BigDecimal.ZERO)).presentCount(userPresents).lateCount(userLates).absentCount(userAbsents).build());
            }

            for (MemberPerformanceDTO mp : performance) {
                totalPresentCount += mp.getPresentCount();
                totalLateCount += mp.getLateCount();
                totalAbsentCount += mp.getAbsentCount();
            }
        }

        long allOverdue = pendingTasksFuture.join();
        long emiOverdue = pendingPaymentsCountFuture.join();
        long followupOverdue = Math.max(0, allOverdue - emiOverdue);

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .presentCount(totalPresentCount).absentCount(totalAbsentCount).halfDayCount(0L)
                .lateCount(totalLateCount)
                .dailyRevenue(dailyRevenueFuture.join()).monthlyRevenue(monthly)
                .expectedRevenue(pendingRevenueFuture.join())
                .pendingPaymentsAmount(pendingRevenueFuture.join()).forecastRevenue(forecastRevenueFuture.join())
                .todayFollowups(todayFollowupsFuture.join()).pendingFollowups(followupOverdue)
                .pendingAppointments(allOverdue)
                .pendingPayments(emiOverdue).monthlyTarget(monthlyTarget)
                .distributedTarget(distributedTarget)
                .targetAchievement(achievement)
                .totalLostCount(totalLostCountFuture.join()).interestedCount(interestedCountFuture.join())
                .totalLeads(totalLeadsCountFuture.join()).convertedCount(convertedCountFuture.join())
                .totalUsers((long) userIdList.size()).todayLeadsCount(todayFollowupsFuture.join())
                .todayPaymentsCount(todayPaymentsCountFuture.join()).completedToday(completedTodayFuture.join())
                .highPriorityFollowups(highPriorityFollowupsFuture.join())
                .activeSupportTickets(activeTicketsFuture.join()).pendingSupportTickets(pendingTicketsFuture.join())
                .resolvedSupportTickets(resolvedTicketsFuture.join()).closedSupportTickets(closedTicketsFuture.join())
                .totalPendingCount(allOverdue).pendingLeadsCount(followupOverdue)
                .pendingPaymentsCount(emiOverdue)
                .overduePaymentsCount(overduePaymentsCountFuture.join())
                .pendingRevenueAmount(pendingRevenueFuture.join())
                .statusDistribution(mappedDistribution).userBreakdown(userBreakdown)
                .performance(performance)
                .dailyTrend(new ArrayList<>(trendMap.values())).build();

        return stats;
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
