package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.dto.RoleDTO;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.service.AdminService;
import com.lms.www.leadmanagement.service.LeadPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private com.lms.www.leadmanagement.service.AttendanceService attendanceService;

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/manager")
    public ResponseEntity<UserDTO> createManager(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(adminService.createManager(userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        return ResponseEntity.ok(adminService.getAllRoles());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(adminService.createUser(userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/users/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(adminService.updateUser(id, userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users/{id}/reset-password-otp")
    public ResponseEntity<Map<String, String>> generateResetOtp(@PathVariable Long id) {
        String otp = adminService.generateResetOtp(id);
        return ResponseEntity.ok(Map.of("otp", otp, "message", "OTP generated successfully"));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users/{id}/verify-reset-otp")
    public ResponseEntity<Map<String, String>> verifyResetOtp(
            @PathVariable Long id, 
            @RequestBody Map<String, String> request) {
        adminService.resetPasswordWithOtp(id, request.get("otp"), request.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/roles")
    public ResponseEntity<RoleDTO> createRole(@RequestBody RoleDTO roleDTO) {
        return ResponseEntity.ok(adminService.createRole(roleDTO));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/permissions")
    public ResponseEntity<java.util.List<String>> getAllPermissions() {
        return ResponseEntity.ok(adminService.getAllPermissions());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/users")
    public ResponseEntity<Page<UserDTO>> getAllUsers(@PageableDefault(size = 2000) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllUsers(pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/leads")
    public ResponseEntity<Page<LeadDTO>> getAllLeads(
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @PageableDefault(size = 2000, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllLeads(pageable, managerId, teamId, userId, from, to));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getLeadStats() {
        return ResponseEntity.ok(adminService.getLeadStats());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @RequestParam(value = "start", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam(value = "end", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "teamId", required = false) Long teamId) {
        User requester = adminService.getCurrentUser();
        return ResponseEntity.ok(adminService.getDashboardStats(start, end, requester, userId, managerId, teamId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/reports/member-performance")
    public ResponseEntity<List<Map<String, Object>>> getMemberPerformance(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "teamId", required = false) Long teamId,
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "start", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam(value = "end", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end) {
        User requester = adminService.getCurrentUser();
        return ResponseEntity.ok(adminService.getMemberPerformanceFiltered(start, end, requester, userId, teamId, managerId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/payments/history")
    public ResponseEntity<List<PaymentDTO>> getPaymentHistory(
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "tlId", required = false) Long tlId,
            @RequestParam(value = "associateId", required = false) Long associateId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate) {
        return ResponseEntity.ok(leadPaymentService.getFilteredPaymentHistory(managerId, userId, tlId, associateId, startDate, endDate, status));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/payments/invoice/{leadId}")
    public ResponseEntity<PaymentDTO> getInvoice(@PathVariable Long leadId) {
        return ResponseEntity.ok(leadPaymentService.generateInvoice(leadId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/payments/{id}")
    public ResponseEntity<PaymentDTO> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(leadPaymentService.updatePaymentStatus(id, payload));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/leads/unassigned")
    public ResponseEntity<java.util.List<LeadDTO>> getUnassignedLeads() {
        return ResponseEntity.ok(adminService.getUnassignedLeads());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/team-leaders")
    public ResponseEntity<List<UserDTO>> getTeamLeaders() {
        return ResponseEntity.ok(leadPaymentService.getTeamLeaders());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/associates/{tlId}")
    public ResponseEntity<List<UserDTO>> getAssociates(@PathVariable Long tlId) {
        return ResponseEntity.ok(adminService.getAssociatesByTl(tlId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/managers")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getManagers() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getManagers()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getTeamsByManager(@RequestParam Long managerId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getTeamsByManager(managerId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/associates")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAssociatesFiltered(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long managerId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAssociates(teamId, managerId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    @GetMapping("/team-tree")
    public ResponseEntity<List<UserDTO>> getTeamTree() {
        return ResponseEntity.ok(adminService.getStaffTree());
    }

    @PreAuthorize("hasAuthority('ASSIGN_TO_TL') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    @PostMapping("/assign-lead/{leadId}/{tlId}")
    public ResponseEntity<LeadDTO> assignLead(@PathVariable Long leadId, @PathVariable Long tlId) {
        return ResponseEntity.ok(adminService.assignLead(leadId, tlId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/assign-supervisor/{assocId}/{supId}")
    public ResponseEntity<UserDTO> assignSupervisor(@PathVariable Long assocId, @PathVariable Long supId) {
        return ResponseEntity.ok(adminService.assignSupervisor(assocId, supId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/bulk-assign-supervisor")
    public ResponseEntity<List<UserDTO>> bulkAssignSupervisor(@RequestBody Map<String, Object> body) {
        List<Long> associateIds = ((List<?>) body.get("associateIds")).stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
        Long supervisorId = Long.valueOf(body.get("supervisorId").toString());
        return ResponseEntity.ok(adminService.bulkAssignSupervisor(associateIds, supervisorId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/bulk-assign-hierarchy")
    public ResponseEntity<Map<String, Object>> bulkAssignHierarchy(@RequestBody Map<String, String> emailMap) {
        return ResponseEntity.ok(adminService.bulkMapAssociates(emailMap));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/leads/bulk-assign")
    public ResponseEntity<List<LeadDTO>> bulkAssignLeads(@RequestBody java.util.Map<String, Object> body) {
        Object leadIdsObj = body.get("leadIds");
        Object tlIdObj = body.get("tlId");
        
        if (tlIdObj == null) {
            tlIdObj = body.get("userId"); // Fallback for userId if present
        }
        
        if (leadIdsObj == null || tlIdObj == null) {
            throw new com.lms.www.leadmanagement.exception.InvalidRequestException("leadIds and targetId are required");
        }

        List<Long> leadIds = ((List<?>) leadIdsObj).stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
        Long tlId = Long.valueOf(tlIdObj.toString());
        return ResponseEntity.ok(adminService.bulkAssignLeads(leadIds, tlId));
    }

    @Autowired
    private com.lms.www.leadmanagement.service.CsvLeadImportService csvLeadImportService;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/upload-old-leads")
    public ResponseEntity<com.lms.www.leadmanagement.dto.LeadUploadResponse> uploadOldLeads(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(csvLeadImportService.importOldLeadsCsv(file));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/shifts")
    public ResponseEntity<List<com.lms.www.leadmanagement.entity.AttendanceShift>> getAllShifts() {
        return ResponseEntity.ok(attendanceService.getAllShifts());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/test")
    public ResponseEntity<String> testAdmin() {
        return ResponseEntity.ok("Admin Connectivity OK");
    }
}
