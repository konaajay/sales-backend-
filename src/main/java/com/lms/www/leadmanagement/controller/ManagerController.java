package com.lms.www.leadmanagement.controller;

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

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/team-leader")
    public ResponseEntity<UserDTO> createTeamLeader(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(managerService.createTeamLeader(userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/team-leaders")
    public ResponseEntity<List<UserDTO>> getTeamLeaders() {
        return ResponseEntity.ok(managerService.getAllManagedUsers());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/team-tree")
    public ResponseEntity<java.util.List<UserDTO>> getTeamTree() {
        User manager = managerService.getCurrentUser();
        return ResponseEntity.ok(java.util.List.of(UserDTO.fromEntityWithTree(manager)));
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
    public ResponseEntity<List<LeadDTO>> getAllLeads(@RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(leadService.getAllLeadsForManager(userId));
    }

    @PreAuthorize("hasAuthority('ASSIGN_TO_TL') or hasAuthority('ASSIGN_TO_ASSOCIATE') or hasAuthority('ADMIN')")
    @PostMapping("/assign-lead/{leadId}/{userId}")
    public ResponseEntity<LeadDTO> assignLead(@PathVariable Long leadId, @PathVariable Long userId) {
        return ResponseEntity.ok(leadService.assignLead(leadId, userId));
    }

    @PreAuthorize("hasAuthority('ASSIGN_TO_TL') or hasAuthority('ASSIGN_TO_ASSOCIATE') or hasAuthority('ADMIN')")
    @PostMapping("/leads/bulk-assign")
    public ResponseEntity<List<LeadDTO>> bulkAssignLeads(@RequestBody Map<String, Object> body) {
        List<Long> leadIds = ((List<?>) body.get("leadIds")).stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
        Long userId = Long.valueOf(body.get("userId").toString());
        return ResponseEntity.ok(leadService.bulkAssignLeads(leadIds, userId));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(managerService.createUser(userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/users/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(managerService.updateUser(id, userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        managerService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/permissions")
    public ResponseEntity<List<String>> getAllPermissions() {
        return ResponseEntity.ok(managerService.getAllPermissions());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/payments/history")
    public ResponseEntity<List<PaymentDTO>> getPaymentHistory(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "tlId", required = false) Long tlId,
            @RequestParam(value = "associateId", required = false) Long associateId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime endDate) {
        if (userId == null && tlId == null && associateId == null) {
            userId = managerService.getCurrentUser().getId();
        }
        return ResponseEntity.ok(leadPaymentService.getFilteredPaymentHistory(userId, tlId, associateId, startDate, endDate, status));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/payments/{id}")
    public ResponseEntity<PaymentDTO> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(leadPaymentService.updatePaymentStatus(id, payload));
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime end) {
        User requester = managerService.getCurrentUser();
        return ResponseEntity.ok(adminService.getDashboardStats(start, end, requester, userId));
    }

    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    @GetMapping("/reports/member-performance")
    public ResponseEntity<List<Map<String, Object>>> getMemberPerformance(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime end) {
        User manager = managerService.getCurrentUser();
        return ResponseEntity.ok(adminService.getMemberPerformanceFiltered(start, end, manager, userId));
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

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        return ResponseEntity.ok(managerService.getAllRoles());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/shifts")
    public ResponseEntity<List<com.lms.www.leadmanagement.entity.AttendanceShift>> getAllShifts() {
        return ResponseEntity.ok(attendanceService.getAllShifts());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/offices")
    public ResponseEntity<List<Map<String, Object>>> getAllOffices() {
        return ResponseEntity.ok(adminService.getAllOffices());
    }
}
