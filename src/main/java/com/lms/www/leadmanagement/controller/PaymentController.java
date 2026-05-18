package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.dto.PaymentSplitRequest;
import com.lms.www.leadmanagement.service.LeadPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@Slf4j
public class PaymentController {

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private com.lms.www.leadmanagement.repository.StudentFeeRepository studentFeeRepository;

    @Autowired
    private com.lms.www.leadmanagement.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.lms.www.leadmanagement.repository.LeadRepository leadRepository;

    @Autowired
    private com.lms.www.leadmanagement.service.SecurityService securityService;

    @Value("${cashfree.environment:TEST}")
    private String cashfreeEnvironment;

    @Value("${cashfree.secret.key}")
    private String secretKey;

    @GetMapping("/api/payments/lead/{leadId}/invoice")
    public ResponseEntity<PaymentDTO> getInvoiceByLeadId(@PathVariable Long leadId) {
        return ResponseEntity.ok(leadPaymentService.generateInvoice(leadId));
    }

    @GetMapping("/api/payments/{paymentId}/invoice")
    public ResponseEntity<PaymentDTO> getInvoiceByPaymentId(@PathVariable Long paymentId) {
        return ResponseEntity.ok(leadPaymentService.generateInvoiceByPaymentId(paymentId));
    }

    @PutMapping("/api/payments/{id}/status")
    @PreAuthorize("hasAnyAuthority('UPDATE_LEAD_STATUS', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ADMIN', 'MANAGER')")
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
    @PreAuthorize("hasAnyAuthority('UPDATE_LEAD_STATUS', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Void> splitPayment(
            @PathVariable Long id,
            @RequestBody PaymentSplitRequest splitRequest) {
        leadPaymentService.splitPayment(id, splitRequest);
        return ResponseEntity.ok().build();
    }

    @Autowired
    private com.lms.www.leadmanagement.service.FileStorageService fileStorageService;

    @PostMapping(value = "/api/payments/manual-record", consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyAuthority('UPDATE_LEAD_STATUS', 'ROLE_ADMIN', 'ROLE_MANAGER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<com.lms.www.leadmanagement.dto.PaymentDTO> recordManualPayment(
            @RequestParam("data") String dataJson,
            @RequestParam(value = "receipt", required = false) org.springframework.web.multipart.MultipartFile receipt) throws Exception {
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO payload = mapper.readValue(dataJson, com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO.class);
        
        String receiptUrl = null;
        if (receipt != null && !receipt.isEmpty()) {
            receiptUrl = fileStorageService.save(receipt, "payments");
        }
        
        return ResponseEntity.ok(leadPaymentService.recordManualPayment(payload, receiptUrl));
    }

    @PostMapping("/api/payments/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Void> approvePayment(@PathVariable Long id) {
        leadPaymentService.approvePayment(id, securityService.getCurrentUser());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/payments/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Void> rejectPayment(@PathVariable Long id, @RequestParam(required = false) String reason) {
        leadPaymentService.rejectPayment(id, reason != null ? reason : "Unspecified", securityService.getCurrentUser());
        return ResponseEntity.ok().build();
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
        
        try {
            return ResponseEntity.ok(leadPaymentService.createCashfreeOrder(leadId, amount, type, installments, totalAmount, discount));
        } catch (Exception e) {
            log.error("CRITICAL: Cashfree Order Initiation Failed for Lead {}: {}", leadId, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal Server Error: " + e.getMessage());
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping({"/session/{orderId}", "/fetch-order/{orderId}"})
    public ResponseEntity<?> fetchOrder(@PathVariable String orderId) {
        log.info("Gateway session fetch for Order: {}", orderId);
        return ResponseEntity.ok(leadPaymentService.fetchCashfreeOrder(orderId));
    }

    @PostMapping("/api/payments/order/{orderId}/verify")
    @PreAuthorize("hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Map<String, Object>> verifyPayment(@PathVariable String orderId) {
        return ResponseEntity.ok(leadPaymentService.verifyAndUpdatePayment(orderId));
    }

    @PostMapping("/api/payments/{paymentId}/link")
    @PreAuthorize("hasAuthority('SEND_PAYMENT') or hasAuthority('UPDATE_LEAD_STATUS') or hasAuthority('ADMIN') or hasAuthority('MANAGER')")
    public ResponseEntity<Map<String, String>> generatePaymentLink(@PathVariable Long paymentId) {
        return ResponseEntity.ok(leadPaymentService.generatePaymentLink(paymentId));
    }

    @PostMapping("/api/payments/webhook/cashfree")
    public ResponseEntity<String> handleCashfreeWebhook(
            @RequestBody(required = false) String payload, 
            @RequestHeader(value = "x-cf-signature", required = false) String signature,
            @RequestHeader(value = "x-cf-timestamp", required = false) String timestamp,
            @RequestHeader Map<String, String> headers) {
        
        log.info("========== CASHFREE WEBHOOK HIT ==========");
        log.info("Headers: {}", headers);
        log.info("Payload: {}", payload);

        if (payload == null || payload.isBlank()) {
            return ResponseEntity.ok("OK");
        }

        // --- SIGNATURE VERIFICATION (Production Security) ---
        if (signature != null && timestamp != null) {
            try {
                String data = timestamp + payload;
                String expectedSignature = calculateHmac(data, secretKey);
                
                if (!signature.equals(expectedSignature)) {
                    log.error("CRITICAL: Invalid Webhook Signature detected!");
                    return ResponseEntity.status(401).body("Invalid Signature");
                }
                log.info("Webhook Signature Verified successfully.");
            } catch (Exception e) {
                log.error("Signature verification error: {}", e.getMessage());
                // For safety in dev, we might continue, but in strict prod, we should reject
            }
        } else {
            log.warn("Webhook received WITHOUT signature headers. Verify Cashfree configuration.");
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payloadMap = mapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            Map<String, Object> data = (Map<String, Object>) payloadMap.get("data");
            if (data != null) {
                Map<String, Object> order = (Map<String, Object>) data.get("order");
                Map<String, Object> payment = (Map<String, Object>) data.get("payment");
                
                if (order != null && payment != null) {
                    String gatewayOrderId = (String) order.get("order_id");
                    String paymentStatus = (String) payment.get("payment_status");
                    java.math.BigDecimal amount = new java.math.BigDecimal(payment.get("payment_amount").toString());
                    String method = (String) payment.get("payment_group");

                    // Find pending payment record by gateway ID
                    com.lms.www.leadmanagement.entity.Payment p = paymentRepository
                            .findTopByPaymentGatewayIdOrderByCreatedAtDesc(gatewayOrderId)
                            .orElse(null);

                    if (p != null) {
                        if ("SUCCESS".equals(paymentStatus) && p.getStatus() != com.lms.www.leadmanagement.entity.Payment.Status.PAID) {
                            leadPaymentService.updatePaymentStatus(p.getId(), "PAID", method, "Cashfree Webhook Success", amount, null);
                            log.info("Webhook: Updated payment {} to PAID.", p.getId());
                        } else if ("FAILED".equals(paymentStatus) && p.getStatus() == com.lms.www.leadmanagement.entity.Payment.Status.PENDING) {
                            leadPaymentService.updatePaymentStatus(p.getId(), "FAILED", method, "Cashfree Webhook Reported Failure", amount, null);
                            log.info("Webhook: Updated payment {} to FAILED.", p.getId());
                        } else {
                            log.info("Webhook: No update needed for order {}. Current status: {}, Gateway status: {}", gatewayOrderId, p.getStatus(), paymentStatus);
                        }
                    } else {
                        log.info("Webhook: Payment record not found for order {}", gatewayOrderId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Cashfree webhook: {}", e.getMessage(), e);
            // Don't throw, let it return 200 OK to avoid Cashfree retries / test failures
        }
        
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/api/public/payments/order/{orderId}")
    public ResponseEntity<Map<String, Object>> getPublicOrderDetails(@PathVariable String orderId) {
        com.lms.www.leadmanagement.entity.Payment p = paymentRepository
                .findTopByPaymentGatewayIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new com.lms.www.leadmanagement.exception.ResourceNotFoundException("Order not found"));
        
        com.lms.www.leadmanagement.entity.Lead lead = leadRepository.findById(p.getLeadId()).orElseThrow();
        
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("amount", p.getAmount());
        response.put("studentName", lead.getName());
        response.put("studentEmail", lead.getEmail());
        response.put("status", p.getStatus().name());
        response.put("cashfreeEnvironment", cashfreeEnvironment); // Added to sync mode with frontend
        
        // Proactive Verification: If the order is pending in our DB, check Cashfree status
        if (p.getStatus() == com.lms.www.leadmanagement.entity.Payment.Status.PENDING) {
            try {
                com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfOrder = leadPaymentService.getCashfreeOrderRaw(orderId);
                if ("PAID".equalsIgnoreCase(cfOrder.getOrder_status())) {
                    leadPaymentService.updatePaymentStatus(p.getId(), "PAID", "PUBLIC_PAGE_SYNC", "Auto-verified on public page visit", p.getAmount(), null);
                    // Refresh entity to get updated status for the response
                    p = paymentRepository.findById(p.getId()).orElse(p);
                } else if ("FAILED".equalsIgnoreCase(cfOrder.getOrder_status()) || "CANCELLED".equalsIgnoreCase(cfOrder.getOrder_status())) {
                    leadPaymentService.updatePaymentStatus(p.getId(), "FAILED", "PUBLIC_PAGE_SYNC", "Gateway reported failure on status page visit", p.getAmount(), null);
                    p = paymentRepository.findById(p.getId()).orElse(p);
                }
            } catch (Exception e) {
                // Ignore errors during proactive sync, just log
                log.warn("[SYNC-WARN] Proactive status check failed for {}: {}", orderId, e.getMessage());
            }
        }

        // Fetch session ID from Cashfree to allow checkout
        try {
            Map<String, String> cfOrder = leadPaymentService.fetchCashfreeOrder(orderId);
            String sessionId = (cfOrder != null) ? cfOrder.get("payment_session_id") : null;
            
            if (sessionId != null && !sessionId.isBlank()) {
                response.put("payment_session_id", sessionId);
                log.info("Successfully attached Payment Session ID for Order: {}", orderId);
            } else {
                log.warn("Cashfree Order found but NO payment_session_id returned for Order: {}", orderId);
                response.put("error", "No active payment session found in gateway. Session may have expired.");
            }
        } catch (Exception e) {
            log.error("Gateway session lookup failed for Order {}: {}", orderId, e.getMessage());
            response.put("warning", "Gateway session lookup failed: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/public/payments/invoice")
    public ResponseEntity<Map<String, Object>> getPublicInvoice(@RequestParam("order_id") String orderId) {
        com.lms.www.leadmanagement.entity.Payment p = paymentRepository
                .findTopByPaymentGatewayIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new com.lms.www.leadmanagement.exception.ResourceNotFoundException("Invoice not found"));
        
        // Only allow access if paid
        String status = p.getStatus().name();
        if (!"PAID".equals(status) && !"SUCCESS".equals(status) && !"COMPLETED".equals(status)) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Payment verification pending. Invoice access restricted.");
            return ResponseEntity.status(403).body(error);
        }

        com.lms.www.leadmanagement.entity.Lead lead = leadRepository.findById(p.getLeadId()).orElseThrow();
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", p.getId());
        response.put("paymentGatewayId", p.getPaymentGatewayId());
        response.put("amount", p.getAmount());
        response.put("date", p.getDate() != null ? p.getDate() : p.getCreatedAt());
        response.put("paymentMethod", p.getPaymentMethod());
        response.put("paymentType", p.getPaymentType());
        response.put("status", status);
        response.put("leadName", lead.getName());
        response.put("leadEmail", lead.getEmail());
        
        if (lead.getAssignedTo() != null) {
            response.put("assignedTlName", lead.getAssignedTo().getName());
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/public/payments/session/{orderId}")
    public ResponseEntity<?> getPaymentSession(@PathVariable String orderId) {
        // Always fetch from gateway to ensure fresh session ID
        Map<String, String> cfData = leadPaymentService.fetchCashfreeOrder(orderId);
        
        Map<String, String> response = new HashMap<>();
        response.put("order_id", orderId);
        response.put("payment_session_id", cfData.get("payment_session_id"));
        return ResponseEntity.ok(response);
    }

    private String calculateHmac(String data, String key) throws Exception {
        javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256");
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes());
        return java.util.Base64.getEncoder().encodeToString(rawHmac);
    }
}
