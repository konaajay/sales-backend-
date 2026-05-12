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
import java.util.List;

@RestController
public class PaymentController {

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private com.lms.www.leadmanagement.repository.StudentFeeRepository studentFeeRepository;

    @Autowired
    private com.lms.www.leadmanagement.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.lms.www.leadmanagement.repository.LeadRepository leadRepository;

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
    public ResponseEntity<Map<String, Object>> getStudentFee(@PathVariable Long leadId) {
        return ResponseEntity.ok(leadPaymentService.getStudentFeeStructure(leadId));
    }

    @PostMapping("/api/payments/cashfree/create-order")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Map<String, String>> createCashfreeOrder(@RequestBody Map<String, Object> payload) {
        if (payload.get("leadId") == null || payload.get("leadId").toString().isBlank()) {
            throw new IllegalArgumentException("Lead ID is required");
        }
        if (payload.get("amount") == null || payload.get("amount").toString().isBlank()) {
            throw new IllegalArgumentException("Amount is required");
        }

        Long leadId = Long.valueOf(payload.get("leadId").toString());
        java.math.BigDecimal amount = new java.math.BigDecimal(payload.get("amount").toString());
        String type = (String) payload.get("type");
        List<Map<String, Object>> installments = (List<Map<String, Object>>) payload.get("installments");
        
        java.math.BigDecimal totalAmount = null;
        if (payload.containsKey("totalAmount") && payload.get("totalAmount") != null && !payload.get("totalAmount").toString().isBlank()) {
            totalAmount = new java.math.BigDecimal(payload.get("totalAmount").toString());
        }

        java.math.BigDecimal discount = null;
        if (payload.containsKey("discount") && payload.get("discount") != null && !payload.get("discount").toString().isBlank()) {
            discount = new java.math.BigDecimal(payload.get("discount").toString());
        }
        
        return ResponseEntity.ok(leadPaymentService.createCashfreeOrder(leadId, amount, type, installments, totalAmount, discount));
    }

    @PostMapping("/api/payments/order/{orderId}/verify")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Map<String, Object>> verifyPayment(@PathVariable String orderId) {
        return ResponseEntity.ok(leadPaymentService.verifyAndUpdatePayment(orderId));
    }

    @PostMapping("/api/payments/{paymentId}/link")
    @PreAuthorize("hasAuthority('SEND_PAYMENT') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Map<String, String>> generatePaymentLink(@PathVariable Long paymentId) {
        return ResponseEntity.ok(leadPaymentService.generatePaymentLink(paymentId));
    }

    @PostMapping("/api/payments/webhook/cashfree")
    public ResponseEntity<String> handleCashfreeWebhook(@RequestBody(required = false) String payload, @RequestHeader Map<String, String> headers) {
        System.out.println("========== CASHFREE WEBHOOK HIT ==========");
        System.out.println("Headers: " + headers);
        System.out.println("Payload: " + payload);
        
        try {
            if (payload == null || payload.isBlank()) {
                return ResponseEntity.ok("OK");
            }
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payloadMap = mapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            Map<String, Object> data = (Map<String, Object>) payloadMap.get("data");
            if (data != null) {
                Map<String, Object> order = (Map<String, Object>) data.get("order");
                Map<String, Object> payment = (Map<String, Object>) data.get("payment");
                
                if (order != null && payment != null && "SUCCESS".equals(payment.get("payment_status"))) {
                    String gatewayOrderId = (String) order.get("order_id");
                    java.math.BigDecimal amount = new java.math.BigDecimal(payment.get("payment_amount").toString());
                    String method = (String) payment.get("payment_group");
                    
                    // Find pending payment record by gateway ID
                    com.lms.www.leadmanagement.entity.Payment p = paymentRepository
                            .findByPaymentGatewayId(gatewayOrderId)
                            .orElse(null);
                    
                    if (p != null && p.getStatus() != com.lms.www.leadmanagement.entity.Payment.Status.PAID) {
                        leadPaymentService.updatePaymentStatus(p.getId(), "PAID", method, "Cashfree Webhook Success", amount, null);
                        System.out.println("Webhook: Updated payment " + p.getId() + " to PAID.");
                    } else {
                        System.out.println("Webhook: Payment not found or already PAID for order " + gatewayOrderId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing Cashfree webhook: " + e.getMessage());
            e.printStackTrace();
            // Don't throw, let it return 200 OK to avoid Cashfree retries / test failures
        }
        
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/api/public/payments/order/{orderId}")
    public ResponseEntity<Map<String, Object>> getPublicOrderDetails(@PathVariable String orderId) {
        com.lms.www.leadmanagement.entity.Payment p = paymentRepository
                .findByPaymentGatewayId(orderId)
                .orElseThrow(() -> new com.lms.www.leadmanagement.exception.ResourceNotFoundException("Order not found"));
        
        com.lms.www.leadmanagement.entity.Lead lead = leadRepository.findById(p.getLeadId()).orElseThrow();
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("amount", p.getAmount());
        response.put("studentName", lead.getName());
        response.put("studentEmail", lead.getEmail());
        
        // Proactive Verification: If the order is pending in our DB, check Cashfree status
        if (p.getStatus() == com.lms.www.leadmanagement.entity.Payment.Status.PENDING) {
            try {
                com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfOrder = leadPaymentService.getCashfreeOrderRaw(orderId);
                if ("PAID".equalsIgnoreCase(cfOrder.getOrder_status())) {
                    leadPaymentService.updatePaymentStatus(p.getId(), "PAID", "PUBLIC_PAGE_SYNC", "Auto-verified on public page visit", p.getAmount(), null);
                    // Refresh entity to get updated status for the response
                    p = paymentRepository.findById(p.getId()).orElse(p);
                }
            } catch (Exception e) {
                // Ignore errors during proactive sync, just log
                System.err.println("[SYNC-WARN] Proactive status check failed for " + orderId + ": " + e.getMessage());
            }
        }

        // Fetch session ID from Cashfree to allow checkout
        try {
            Map<String, String> cfOrder = leadPaymentService.fetchCashfreeOrder(orderId);
            if (cfOrder != null && cfOrder.containsKey("payment_session_id")) {
                response.put("paymentSessionId", cfOrder.get("payment_session_id"));
            } else {
                response.put("error", "No active payment session found in gateway");
            }
        } catch (Exception e) {
            response.put("warning", "Gateway session lookup failed: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
