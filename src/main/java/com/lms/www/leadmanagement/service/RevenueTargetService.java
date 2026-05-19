package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.RevenueTarget;
import com.lms.www.leadmanagement.entity.TargetType;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import com.lms.www.leadmanagement.repository.RevenueTargetRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class RevenueTargetService {

    private final RevenueTargetRepository targetRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final SecurityService securityService;

    @Transactional
    public RevenueTarget setTarget(Long userId, BigDecimal amount, Integer month, Integer year) {
        User currentUser = securityService.getCurrentUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        securityService.validateHierarchyAccess(currentUser, targetUser);
        


        RevenueTarget target = targetRepository.findTopByUserIdAndMonthAndYearAndTypeAndAssignedByOrderByIdDesc(
                userId, month, year, TargetType.ASSIGNED, currentUser.getId()).orElse(new RevenueTarget());
        
        target.setUser(targetUser);
        target.setMonth(month);
        target.setYear(year);
        target.setType(TargetType.ASSIGNED);
        target.setAssignedBy(currentUser.getId());
        target.setTargetAmount(amount);
        target.setUpdatedAt(LocalDateTime.now());
        if (target.getId() == null) {
            target.setCreatedAt(LocalDateTime.now());
        }
        
        return targetRepository.save(target);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bulkSetTargets(List<Map<String, Object>> payloads) {
        User currentUser = securityService.getCurrentUser();
        if (payloads == null || payloads.isEmpty()) return;

        // 1. Strategic Parity Enforcement
        if (securityService.isTeamLeader(currentUser)) {
            Integer month = Integer.valueOf(payloads.get(0).get("month").toString());
            Integer year = Integer.valueOf(payloads.get(0).get("year").toString());
            
            List<RevenueTarget> targets = targetRepository.findAssignedTarget(currentUser.getId(), month, year);
            BigDecimal assignedTarget = targets.isEmpty() ? BigDecimal.ZERO : targets.get(0).getTargetAmount();
            
            BigDecimal totalDistributed = payloads.stream()
                    .map(p -> new BigDecimal(p.get("amount").toString()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (assignedTarget.compareTo(BigDecimal.ZERO) > 0) {
                if (assignedTarget.subtract(totalDistributed).abs().compareTo(new BigDecimal("1.0")) > 0) {
                    throw new RuntimeException("Strategic Imbalance: Total allocation must match your assigned target.");
                }
            }
        }

        // 2. Upsert Targets
        for (Map<String, Object> payload : payloads) {
            try {
                Long userId = Long.valueOf(payload.get("userId").toString());
                BigDecimal amount = new BigDecimal(payload.get("amount").toString());
                Integer month = Integer.valueOf(payload.get("month").toString());
                Integer year = Integer.valueOf(payload.get("year").toString());
                
                String typeStr = payload.getOrDefault("type", "ASSIGNED").toString();
                TargetType type = TargetType.valueOf(typeStr);

                User targetUser = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                RevenueTarget target = targetRepository.findTopByUserIdAndMonthAndYearAndTypeAndAssignedByOrderByIdDesc(
                        userId, month, year, type, currentUser.getId()).orElse(new RevenueTarget());

                target.setUser(targetUser);
                target.setMonth(month);
                target.setYear(year);
                target.setType(type);
                target.setAssignedBy(currentUser.getId());
                target.setTargetAmount(amount);
                target.setUpdatedAt(LocalDateTime.now());
                if (target.getId() == null) {
                    target.setCreatedAt(LocalDateTime.now());
                }

                targetRepository.save(target);
            } catch (Exception e) {
                log.error("[TARGET-BULK] Failure: {}", e.getMessage());
                throw new RuntimeException("Save failed: " + e.getMessage());
            }
        }
        targetRepository.flush();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTargetSummary(Long userId, Integer month, Integer year) {
        List<RevenueTarget> budgetTargets = targetRepository.findAssignedBudget(userId, month, year);
        BigDecimal assignedTarget = budgetTargets.isEmpty() ? BigDecimal.ZERO : budgetTargets.get(0).getTargetAmount();
        
        BigDecimal distributed = targetRepository.getDistributedTotal(userId, month, year);
        if (distributed == null) distributed = BigDecimal.ZERO;

        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("assignedTarget", assignedTarget);
        map.put("distributedAmount", distributed);
        map.put("month", month);
        map.put("year", year);
        return map;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTargetHistory(Long userId) {
        User targetUser = userRepository.findById(userId).orElseThrow();
        securityService.validateHierarchyAccess(securityService.getCurrentUser(), targetUser);

        // Fetch latest targets per month/year regardless of type
        List<RevenueTarget> latestTargets = targetRepository.findAllByUserIdLatest(userId);
        
        // Group by month/year to handle potential overlaps, prioritizing self-assignment if it exists
        Map<String, RevenueTarget> consolidated = new HashMap<>();
        for (RevenueTarget t : latestTargets) {
            String key = t.getYear() + "-" + t.getMonth();
            // Prioritize target where assignedBy == userId (Self-assigned personal goal)
            if (!consolidated.containsKey(key) || t.getAssignedBy().equals(userId)) {
                consolidated.put(key, t);
            }
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (RevenueTarget t : consolidated.values()) {
            LocalDateTime start = LocalDate.of(t.getYear(), t.getMonth(), 1).atStartOfDay();
            LocalDateTime end = LocalDate.of(t.getYear(), t.getMonth(), 1).plusMonths(1).atStartOfDay();
            
            BigDecimal achieved = paymentRepository.getTotalRevenueIn(Collections.singletonList(userId), start, end);
            if (achieved == null) achieved = BigDecimal.ZERO;

            Map<String, Object> entry = new HashMap<>();
            entry.put("id", t.getId());
            entry.put("month", t.getMonth());
            entry.put("year", t.getYear());
            entry.put("targetAmount", t.getTargetAmount());
            entry.put("achievedAmount", achieved);
            entry.put("assignedBy", t.getAssignedBy());
            entry.put("createdAt", t.getCreatedAt());
            entry.put("achievementRate", t.getTargetAmount().compareTo(BigDecimal.ZERO) > 0 
                ? achieved.divide(t.getTargetAmount(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                : BigDecimal.ZERO);
            
            history.add(entry);
        }
        return history;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTargetsAssignedBy(Long assignerId, Integer month, Integer year) {
        List<RevenueTarget> latestAssignments = targetRepository.findAllByAssignedByAndMonthAndYear(assignerId, month, year);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (RevenueTarget rt : latestAssignments) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", rt.getUser().getId());
            map.put("amount", rt.getTargetAmount());
            map.put("month", rt.getMonth());
            map.put("year", rt.getYear());
            map.put("type", rt.getType());
            result.add(map);
        }
        return result;
    }
}
