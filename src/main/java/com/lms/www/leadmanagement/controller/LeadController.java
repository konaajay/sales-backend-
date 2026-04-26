package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.service.LeadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;
import com.lms.www.leadmanagement.dto.BulkUploadResponseDTO;
import com.lms.www.leadmanagement.service.LeadBulkUploadService;


@RestController

@RequestMapping("/api/leads")
public class LeadController {

    @Autowired
    private LeadService leadService;

    @Autowired
    private LeadBulkUploadService bulkUploadService;

    @Autowired
    private com.lms.www.leadmanagement.service.LeadPaymentService leadPaymentService;


    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    public ResponseEntity<Map<String, Object>> getLeadStats() {
        return ResponseEntity.ok(leadService.getLeadStats());
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    public ResponseEntity<List<LeadDTO>> getMyLeads() {
        return ResponseEntity.ok(leadService.getMyLeads());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    public ResponseEntity<LeadDTO> getLeadById(@PathVariable Long id) {
        return ResponseEntity.ok(leadService.getLeadById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_LEADS')")
    public ResponseEntity<LeadDTO> createLead(@RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.createLead(leadDTO));
    }

    @PostMapping("/bulk-upload")
    @PreAuthorize("hasAuthority('BULK_UPLOAD')")
    public ResponseEntity<BulkUploadResponseDTO> bulkUploadLeads(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(bulkUploadService.uploadLeads(file, null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    public ResponseEntity<LeadDTO> updateLead(@PathVariable Long id, @RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.updateLead(id, leadDTO));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS')")
    public ResponseEntity<LeadDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody com.lms.www.leadmanagement.dto.StatusUpdateRequest request) {
        return ResponseEntity.ok(leadService.updateStatus(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS')")
    public ResponseEntity<LeadDTO> rejectLead(
            @PathVariable Long id,
            @RequestBody Map<String, Object> rejectionData) {
        return ResponseEntity.ok(leadService.rejectLead(id, rejectionData));
    }

    @PostMapping("/{id}/record-outcome")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS')")
    public ResponseEntity<LeadDTO> recordCallOutcome(
            @PathVariable Long id,
            @RequestBody Map<String, Object> outcomeData) {
        return ResponseEntity.ok(leadService.recordCallOutcome(id, outcomeData));
    }

    @GetMapping("/{id}/fee-structure")
    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    public ResponseEntity<Map<String, Object>> getStudentFee(@PathVariable Long id) {
        return ResponseEntity.ok(leadPaymentService.getStudentFeeStructure(id));
    }

}
