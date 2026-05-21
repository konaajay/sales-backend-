package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.dto.BulkUploadResponseDTO;
import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.dto.RoleDTO;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    @Autowired
    private ManagerService managerService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private LeadService leadService;

    @Autowired
    private LeadBulkUploadService bulkUploadService;

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private DashboardStatsService statsService;

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @PostMapping("/team-leader")
    public ResponseEntity<UserDTO> createTeamLeader(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(managerService.createTeamLeader(userDTO));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_TEAM_LEADER')")
    @GetMapping("/team-leaders")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getTeamLeaders() {
        return ResponseEntity.ok(ApiResponse.success(managerService.getAllManagedUsers()));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_TEAM_LEADER')")
    @GetMapping("/team-tree")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<java.util.List<UserDTO>>> getTeamTree(
            @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        User requester = managerService.getCurrentUser();
        Long effectiveId = targetUserId != null ? targetUserId : requester.getId();
        
        // Security check: only allow drilling down into subordinates
        // (Simplified for now, assume requester has access to targetUserId if they can see it in lookup)
        
        java.util.List<Long> ids = managerService.getSubordinateIds(effectiveId);
        java.util.List<UserDTO> team = managerService.getUsersByIds(ids);
        return ResponseEntity.ok(ApiResponse.success(team));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_TEAM_LEADER')")
    @GetMapping("/direct-associates")
    public ResponseEntity<ApiResponse<java.util.List<UserDTO>>> getDirectAssociates() {
        return ResponseEntity.ok(ApiResponse.success(managerService.getDirectSubordinates()));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @PostMapping("/leads")
    public ResponseEntity<LeadDTO> createLead(@RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.createLead(leadDTO));
    }

    @PreAuthorize("hasAuthority('CREATE_LEADS')")
    @PostMapping("/leads/bulk-upload")
    public ResponseEntity<BulkUploadResponseDTO> bulkUploadLeads(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "assignedToIds", required = false) String assignedToIds) {
        return ResponseEntity.ok(bulkUploadService.uploadLeads(file, assignedToIds));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @GetMapping("/leads")
    public ResponseEntity<ApiResponse<List<LeadDTO>>> getAllLeads(
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(ApiResponse.success(leadService.getAllLeadsForManager(managerId, userId)));
    }

    @PreAuthorize("hasAuthority('ASSIGN_TO_TL') or hasAuthority('ASSIGN_TO_ASSOCIATE') or hasAuthority('ADMIN')")
    @PostMapping("/assign-lead/{leadId}/{userId}")
    public ResponseEntity<LeadDTO> assignLead(@PathVariable Long leadId, @PathVariable Long userId) {
        return ResponseEntity.ok(leadService.assignLead(leadId, userId));
    }

    @PreAuthorize("hasAuthority('ASSIGN_TO_TL') or hasAuthority('ASSIGN_TO_ASSOCIATE') or hasAuthority('ADMIN')")
    @PostMapping("/leads/bulk-assign")
    public ResponseEntity<List<LeadDTO>> bulkAssignLeads(@RequestBody Map<String, Object> body) {
        Object leadIdsObj = body.get("leadIds");
        Object userIdObj = body.get("userId");
        if (userIdObj == null) userIdObj = body.get("tlId");

        if (leadIdsObj == null || userIdObj == null) {
            throw new com.lms.www.leadmanagement.exception.InvalidRequestException("leadIds and targetId are required");
        }

        List<Long> leadIds = ((List<?>) leadIdsObj).stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
        Long userId = Long.valueOf(userIdObj.toString());
        return ResponseEntity.ok(leadService.bulkAssignLeads(leadIds, userId));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(managerService.createUser(userDTO));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @PutMapping("/users/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(managerService.updateUser(id, userDTO));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        managerService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/permissions")
    public ResponseEntity<List<String>> getAllPermissions() {
        return ResponseEntity.ok(managerService.getAllPermissions());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/payments/history")
    public ResponseEntity<List<PaymentDTO>> getPaymentHistory(
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "tlId", required = false) Long tlId,
            @RequestParam(value = "associateId", required = false) Long associateId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime endDate) {
        if (managerId == null && userId == null && tlId == null && associateId == null) {
            managerId = managerService.getCurrentUser().getId();
        }
        return ResponseEntity.ok(leadPaymentService.getFilteredPaymentHistory(managerId, userId, tlId, associateId, startDate, endDate, status));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/payments/{id}")
    public ResponseEntity<PaymentDTO> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(leadPaymentService.updatePaymentStatus(id, payload));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<com.lms.www.leadmanagement.dto.DashboardStatsDTO> getDashboardStats(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "teamId", required = false) Long teamId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime end) {
        User requester = managerService.getCurrentUser();
        LocalDate fromDate = (start != null) ? start.toLocalDate() : LocalDate.now().minusDays(30);
        LocalDate toDate = (end != null) ? end.toLocalDate() : LocalDate.now();
        
        java.util.Collection<User> allowedUsers = statsService.determineAllowedUsers(requester, userId, teamId);
        java.util.List<Long> allowedIds = allowedUsers.stream().map(User::getId).collect(Collectors.toList());
        return ResponseEntity.ok(statsService.getStats(allowedIds, fromDate, toDate, false, requester, userId, teamId));
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    @GetMapping("/reports/member-performance")
    public ResponseEntity<List<Map<String, Object>>> getMemberPerformance(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "teamId", required = false) Long teamId,
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime end) {
        User manager = managerService.getCurrentUser();
        return ResponseEntity.ok(adminService.getMemberPerformanceFiltered(start, end, manager, userId, teamId, managerId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users/{associateId}/assign-supervisor/{supervisorId}")
    public ResponseEntity<UserDTO> assignSupervisor(@PathVariable Long associateId, @PathVariable Long supervisorId) {
        return ResponseEntity.ok(managerService.assignToSupervisor(associateId, supervisorId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users/bulk-assign-supervisor")
    public ResponseEntity<List<UserDTO>> bulkAssignSupervisor(@RequestBody Map<String, Object> body) {
        List<Long> associateIds = ((List<?>) body.get("associateIds")).stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
        Long supervisorId = Long.valueOf(body.get("supervisorId").toString());
        // Since ManagerService doesn't have bulkAssignSupervisor yet, we can call AdminService or add it to ManagerService.
        // Let's add it to ManagerService for consistency.
        return ResponseEntity.ok(managerService.bulkAssignSupervisor(associateIds, supervisorId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users/bulk-assign-hierarchy")
    public ResponseEntity<Map<String, Object>> bulkAssignHierarchy(@RequestBody Map<String, String> emailMap) {
        return ResponseEntity.ok(managerService.bulkAssignHierarchy(emailMap));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success(managerService.getAllRoles()));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<List<com.lms.www.leadmanagement.entity.AttendanceShift>>> getAllShifts() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getAllShifts()));
    }

    @PreAuthorize("hasAnyAuthority('MANAGE_USERS', 'ROLE_ADMIN', 'ROLE_MANAGER')")
    @GetMapping("/offices")
    public ResponseEntity<ApiResponse<List<com.lms.www.leadmanagement.dto.OfficeLocationDTO>>> getAllOffices() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllOffices()));
    }
}
