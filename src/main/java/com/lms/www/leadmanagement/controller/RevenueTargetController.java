package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import com.lms.www.leadmanagement.service.RevenueTargetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
@Slf4j
public class RevenueTargetController {

    @Autowired
    private RevenueTargetService targetService;

    @Autowired
    private PaymentRepository paymentRepository;

    @PostMapping("/api/targets/set")
    public ResponseEntity<ApiResponse<?>> setTarget(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(ApiResponse.success(targetService.setTarget(
                Long.valueOf(payload.get("userId").toString()),
                new BigDecimal(payload.get("amount").toString()),
                payload.containsKey("month") ? Integer.valueOf(payload.get("month").toString()) : LocalDate.now().getMonthValue(),
                payload.containsKey("year") ? Integer.valueOf(payload.get("year").toString()) : LocalDate.now().getYear()
            )));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/targets/v2/user/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTargetForUser(@PathVariable Long userId, @RequestParam Integer month, @RequestParam Integer year) {
        try {
            return ResponseEntity.ok(ApiResponse.success(targetService.getTargetSummary(userId, month, year)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/targets/bulk")
    public ResponseEntity<ApiResponse<?>> bulkSetTargets(@RequestBody List<Map<String, Object>> payloads) {
        try {
            targetService.bulkSetTargets(payloads);
            return ResponseEntity.ok(ApiResponse.success("Targets versioned and saved successfully"));
        } catch (Exception e) {
            log.error("[TARGET-ERROR] Failed to save bulk targets: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/targets/history/{userId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTargetHistory(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(targetService.getTargetHistory(userId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/targets/achievement/{userId}")
    public ResponseEntity<ApiResponse<List<Payment>>> getAchievementBreakdown(
            @PathVariable Long userId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
            LocalDateTime end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
            
            List<Payment> payments = paymentRepository.findFilteredByUserIds(
                Collections.singletonList(userId), start, end);
            
            return ResponseEntity.ok(ApiResponse.success(payments));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/targets/bulk/assigned-by/{assignerId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTargetsAssignedBy(
            @PathVariable Long assignerId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            return ResponseEntity.ok(ApiResponse.success(targetService.getTargetsAssignedBy(assignerId, month, year)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/targets/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
