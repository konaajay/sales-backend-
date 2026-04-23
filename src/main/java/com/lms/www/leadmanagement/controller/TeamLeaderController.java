package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.BulkUploadResponseDTO;
import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.service.LeadPaymentService;
import com.lms.www.leadmanagement.service.LeadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("/api/tl")
@RequiredArgsConstructor
@Slf4j
public class TeamLeaderController {

    @Autowired
    private LeadService leadService;

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private com.lms.www.leadmanagement.service.AdminService adminService;

    @Autowired
    private com.lms.www.leadmanagement.repository.UserRepository userRepository;

    @Autowired
    private com.lms.www.leadmanagement.service.LeadBulkUploadService bulkUploadService;

    @PreAuthorize("hasAuthority('BULK_UPLOAD')")
    @PostMapping("/leads/bulk-upload")
    public ResponseEntity<BulkUploadResponseDTO> bulkUploadLeads(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "assignedToIds", required = false) String assignedToIds) {
        return ResponseEntity.ok(bulkUploadService.uploadLeads(file, assignedToIds));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @PostMapping("/leads")
    public ResponseEntity<LeadDTO> createLeadByTL(@RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.createLead(leadDTO));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @GetMapping("/leads/my")
    public ResponseEntity<List<LeadDTO>> getMyLeads() {
        return ResponseEntity.ok(leadService.getMyLeads());
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @GetMapping("/leads/team")
    public ResponseEntity<List<LeadDTO>> getTeamLeads(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate) {
        return ResponseEntity.ok(leadService.getLeadsForTeamLeader(startDate, endDate, userId));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @GetMapping("/leads/stats")
    public ResponseEntity<Map<String, Long>> getMyLeadStats() {
        List<LeadDTO> myLeads = leadService.getMyLeads();
        Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("TOTAL", (long) myLeads.size());
        stats.put("INTERESTED", myLeads.stream().filter(l -> "INTERESTED".equals(l.getStatus())).count());
        stats.put("PAID", myLeads.stream().filter(l -> "PAID".equals(l.getStatus())).count());
        stats.put("NOT_INTERESTED", myLeads.stream().filter(l -> "NOT_INTERESTED".equals(l.getStatus())).count());
        return ResponseEntity.ok(stats);
    }

    @PreAuthorize("hasAuthority('SEND_PAYMENT')")
    @PostMapping("/leads/{id}/send-payment-link")
    public ResponseEntity<Map<String, Object>> sendPaymentLink(
            @PathVariable Long id,
            @RequestBody com.lms.www.leadmanagement.dto.LeadPaymentRequestDTO request) {
        
        BigDecimal initialAmount = request.getInitialAmount();
        if (initialAmount == null) {
            initialAmount = new BigDecimal("499");
        }
        
        String note = request.getNote();
        
        // Ensure status is INTERESTED before generating link
        leadService.updateStatus(id, "INTERESTED", note);
        
        com.lms.www.leadmanagement.dto.PaymentSplitRequest split = null;
        if ("PART".equals(request.getPaymentType())) {
            split = request.toSplitRequest();
        }

        Map<String, String> cfResponse = leadPaymentService.createPaymentLink(id, initialAmount, request.getTotalAmount(), split);
        LeadDTO updatedLead = leadService.getLeadById(id);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("payment_url", cfResponse.get("payment_url"));
        response.put("payment_session_id", cfResponse.get("payment_session_id"));
        response.put("lead", updatedLead);
        
        return ResponseEntity.ok(response);
    }


    @PreAuthorize("hasAuthority('SEND_PAYMENT')")
    @PostMapping("/leads/{id}/mark-paid")
    public ResponseEntity<Map<String, String>> markPaid(@PathVariable Long id) {
        leadPaymentService.markAsPaid(id);
        return ResponseEntity.ok(Map.of("message", "Lead marked as paid and user account created."));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @PutMapping("/leads/{id}/status")
    public ResponseEntity<LeadDTO> updateStatus(
            @PathVariable Long id, 
            @RequestParam String status,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(leadService.updateStatus(id, status, note));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @PostMapping("/leads/{id}/reject")
    public ResponseEntity<LeadDTO> rejectLead(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> rejectionData) {
        return ResponseEntity.ok(leadService.rejectLead(id, rejectionData));
    }

    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    @PutMapping("/leads/{id}/note")
    public ResponseEntity<LeadDTO> updateNote(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(leadService.updateNote(id, body.get("note")));
    }

    @PreAuthorize("hasAuthority('SEND_PAYMENT')")
    @PutMapping("/leads/{id}/payment-link")
    public ResponseEntity<LeadDTO> updatePaymentLink(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(leadService.updatePaymentLink(id, body.get("link")));
    }

    @PreAuthorize("hasAnyAuthority('VIEW_LEADS', 'ROLE_TEAM_LEADER', 'ROLE_MANAGER')")
    @GetMapping("/payments/history")
    public ResponseEntity<List<PaymentDTO>> getPaymentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate) {
        return ResponseEntity.ok(leadPaymentService.getFilteredPaymentHistoryForTL(userDetails.getUsername(), startDate, endDate, status, userId));
    }

    @PreAuthorize("hasAuthority('ASSIGN_TO_ASSOCIATE')")
    @PostMapping("/leads/{leadId}/assign/{associateId}")
    public ResponseEntity<LeadDTO> assignLeadToAssociate(@PathVariable Long leadId, @PathVariable Long associateId) {
        return ResponseEntity.ok(leadService.assignLead(leadId, associateId));
    }

    @PreAuthorize("hasAnyAuthority('VIEW_LEADS', 'ROLE_TEAM_LEADER', 'ROLE_MANAGER')")
    @GetMapping("/subordinates")
    public ResponseEntity<List<UserDTO>> getMySubordinates() {
        return ResponseEntity.ok(leadService.getCurrentUserSubordinates());
    }

    @PreAuthorize("hasAnyAuthority('VIEW_LEADS', 'ROLE_TEAM_LEADER', 'ROLE_MANAGER')")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<java.util.Map<String, Object>> getDashboardStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "start", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam(value = "end", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end) {
        com.lms.www.leadmanagement.entity.User tl = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(adminService.getDashboardStats(start, end, tl, userId));
    }

    @PreAuthorize("hasAnyAuthority('VIEW_LEADS', 'ROLE_TEAM_LEADER', 'ROLE_MANAGER')")
    @GetMapping("/reports/member-performance")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMemberPerformance(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "start", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam(value = "end", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end) {
        com.lms.www.leadmanagement.entity.User tl = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(adminService.getMemberPerformanceFiltered(start, end, tl, userId));
    }
}
