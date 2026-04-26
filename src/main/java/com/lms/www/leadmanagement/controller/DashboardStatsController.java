package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.DashboardStatsDTO;
import com.lms.www.leadmanagement.dto.DashboardSummaryDTO;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.service.DashboardStatsService;
import com.lms.www.leadmanagement.service.ManagerService;
import com.lms.www.leadmanagement.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardStatsController {

    @Autowired
    private DashboardStatsService statsService;

    @Autowired
    private ManagerService managerService;

    @Autowired
    private AdminService adminService;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER', 'ASSOCIATE')")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getUnifiedSummary(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long teamId) {
        User user = managerService.getCurrentUser();
        return ResponseEntity.ok(statsService.getUnifiedSummary(user, from, to, userId, teamId));
    }
}
