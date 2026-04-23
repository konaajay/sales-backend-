package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.dto.PaymentSplitRequest;
import com.lms.www.leadmanagement.service.LeadPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class PaymentController {

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private com.lms.www.leadmanagement.repository.StudentFeeRepository studentFeeRepository;

    @GetMapping("/api/payments/lead/{leadId}/invoice")
    public ResponseEntity<PaymentDTO> getInvoiceByLeadId(@PathVariable Long leadId) {
        return ResponseEntity.ok(leadPaymentService.generateInvoice(leadId));
    }

    @PutMapping("/api/payments/{id}/status")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Void> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String actualPaidAmount,
            @RequestParam(required = false) String nextDueDate) {
        
        Map<String, String> payload = new HashMap<>();
        payload.put("status", status);
        payload.put("paymentMethod", paymentMethod);
        payload.put("note", note);
        payload.put("actualPaidAmount", actualPaidAmount);
        payload.put("nextDueDate", nextDueDate);
        
        leadPaymentService.updatePaymentStatus(id, payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/payments/{id}/split")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Void> splitPayment(
            @PathVariable Long id,
            @RequestBody PaymentSplitRequest splitRequest) {
        leadPaymentService.splitPayment(id, splitRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/payments/manual-record")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<com.lms.www.leadmanagement.dto.PaymentDTO> recordManualPayment(
            @RequestBody java.util.Map<String, Object> payload) {
        return ResponseEntity.ok(leadPaymentService.recordManualPayment(payload));
    }

    @GetMapping("/api/payments/lead/{leadId}/fee-structure")
    @PreAuthorize("hasAuthority('VIEW_LEADS')")
    public ResponseEntity<com.lms.www.leadmanagement.entity.StudentFee> getStudentFee(@PathVariable Long leadId) {
        return ResponseEntity.ok(studentFeeRepository.findByLeadId(leadId).orElse(null));
    }
}
