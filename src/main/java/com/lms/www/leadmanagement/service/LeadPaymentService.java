package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.dto.PaymentSplitRequest;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.exception.InvalidRequestException;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadPaymentService {

    // --- REPOS AND DEPENDENCIES ---
    private final LeadRepository leadRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final LeadTaskRepository leadTaskRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final MailService mailService;
    private final SecurityService securityService;
    private final CashfreeService cashfreeService;
    private final StudentFeeService studentFeeService;
    private final LeadTaskService leadTaskService;

    @Value("${cashfree.webhook.url}")
    private String webhookUrl;

    @Value("${app.frontend-url:http://52.87.168.111}")
    private String frontendUrl;

    // --- CONSTANTS ---
    private static final String CONST_BUSINESS_NAME = "Gyantrix";
    private static final String CONST_BUSINESS_ADDRESS = "Pathrika Nagar, Street No:1, HITEC City, Hyderabad - 500081";
    private static final String CONST_BUSINESS_CONTACT = "+91 9247551330";
    private static final String CONST_BUSINESS_EMAIL = "support@gyantrixacademy.com";
    private static final String CONST_TAX_ID = "GSTIN: 36AAACG1234F1Z5";
    private static final String TYPE_EMI_INSTALLMENT = "EMI_INSTALLMENT";
    private static final String STATUS_CONVERTED = "CONVERTED";

    // --- CASHFREE ORDER GENERATION ---

    @Transactional
    public Map<String, String> createCashfreeOrder(Long leadId, BigDecimal amount, String type,
            List<Map<String, Object>> plannedInstallments, BigDecimal totalAmount, BigDecimal discount) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        BigDecimal minAmount = new BigDecimal("500");
        if (lead.getCourse() != null && lead.getCourse().getMinTokenAmount() != null) {
            minAmount = lead.getCourse().getMinTokenAmount();
        }

        if (amount.compareTo(minAmount) < 0) {
            throw new InvalidRequestException("Minimum payment amount is ₹" + minAmount);
        }

        // --- STRICT ACCOUNTING VALIDATION ---
        BigDecimal finalSettlement = (totalAmount != null ? totalAmount : BigDecimal.ZERO)
                .subtract(discount != null ? discount : BigDecimal.ZERO);

        BigDecimal totalPlanned = amount;
        if (plannedInstallments != null) {
            for (Map<String, Object> inst : plannedInstallments) {
                totalPlanned = totalPlanned.add(new BigDecimal(inst.get("amount").toString()));
            }
        }

        BigDecimal totalAccounted = totalPlanned;
        if (finalSettlement.compareTo(BigDecimal.ZERO) > 0 && totalAccounted.compareTo(finalSettlement) != 0) {
            throw new InvalidRequestException("Accounting Protocol Violation: Total Commitment (₹" + totalAccounted
                    + ") does not match Final Settlement (₹" + finalSettlement + "). Remaining to plan: ₹"
                    + finalSettlement.subtract(amount));
        }

        String orderId = "ORDER_" + leadId + "_" + System.currentTimeMillis();

        // Reusable Cashfree request builder
        com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest request = buildCashfreeRequest(lead, orderId, amount);

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfResponse = cashfreeService.createOrder(request);

        if (cfResponse == null || cfResponse.getPayment_session_id() == null) {
            log.error("CRITICAL: Cashfree order creation failed. Response: {}", cfResponse);
            throw new RuntimeException(
                    "Gateway Order Initiation Failed. Verify customer details and API credentials. Response: " + cfResponse);
        }

        // Record pending payment (Token/Initial)
        Payment payment = Payment.builder()
                .leadId(leadId)
                .amount(amount)
                .status(Payment.Status.PENDING)
                .paymentGatewayId(orderId)
                .paymentType(type != null ? type : "CASHFREE")
                .build();
        paymentRepository.save(payment);

        // Record planned installments if any
        BigDecimal totalCommitment = amount;
        LocalDateTime firstInstallmentDate = null;

        // Clean up any existing pending/planned installments to prevent duplicates
        paymentRepository.deleteByLeadIdAndStatusAndPaymentType(leadId, Payment.Status.PENDING, TYPE_EMI_INSTALLMENT);

        if (plannedInstallments != null && !plannedInstallments.isEmpty()) {
            boolean isFirstSkipped = false;
            for (Map<String, Object> inst : plannedInstallments) {
                BigDecimal instAmount = new BigDecimal(inst.get("amount").toString());
                totalCommitment = totalCommitment.add(instAmount);

                String dueStr = (String) inst.get("dueDate");
                LocalDateTime dueDate = parseDate(dueStr);

                // CRITICAL DUP CHECK
                LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
                if (dueDate != null && dueDate.isBefore(todayStart.plusDays(1))) {
                    continue;
                }
                if (!isFirstSkipped && amount.compareTo(BigDecimal.ZERO) > 0 && instAmount.compareTo(amount) == 0) {
                    isFirstSkipped = true;
                    continue;
                }

                if (firstInstallmentDate == null)
                    firstInstallmentDate = dueDate;

                paymentRepository.save(Payment.builder()
                        .leadId(leadId)
                        .amount(instAmount)
                        .status(Payment.Status.PENDING)
                        .paymentType(TYPE_EMI_INSTALLMENT)
                        .dueDate(dueDate)
                        .note("Planned during initial link generation")
                        .build());

                if (dueDate != null) {
                    leadTaskService.createLeadTask(lead, dueDate, "EMI Collection - Planned", "EMI_COLLECTION");
                }
            }
        }

        // Initialize/Sync Student Fee Structure
        studentFeeService.syncStudentFee(lead, BigDecimal.ZERO, totalAmount != null ? totalAmount : totalCommitment, discount, firstInstallmentDate);

        // Set Payment Status to indicate online payment link sent & pending
        String currentStatus = lead.getStatus() != null ? lead.getStatus().toUpperCase() : "PRE_PAYMENT";
        if (currentStatus.startsWith("POST_PAYMENT")) {
            lead.setStatus(currentStatus + "_PENDING");
        } else if (currentStatus.startsWith("PRE_PAYMENT") || currentStatus.startsWith("PRE-PAYMENT")) {
            lead.setStatus("PRE_PAYMENT_PENDING");
        } else {
            lead.setStatus("POST_PAYMENT_PENDING");
        }
        leadRepository.save(lead);

        // AUTO-EMAIL student the payment request link (Refactored using Thymeleaf templates)
        String paymentUrl = frontendUrl + "/payment-instruction/" + orderId;
        mailService.sendPaymentLink(lead.getEmail(), paymentUrl);

        Map<String, String> result = new HashMap<>();
        result.put("payment_session_id", cfResponse.getPayment_session_id());
        result.put("paymentSessionId", cfResponse.getPayment_session_id());
        result.put("order_id", orderId);
        result.put("payment_url", paymentUrl);
        return result;
    }

    @Transactional
    public Map<String, Object> verifyAndUpdatePayment(String orderId) {
        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfOrder = cashfreeService.getOrder(orderId);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("gatewayStatus", cfOrder.getOrder_status());

        if ("PAID".equalsIgnoreCase(cfOrder.getOrder_status())) {
            Payment p = paymentRepository.findTopByPaymentGatewayIdOrderByCreatedAtDesc(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found in DB"));

            if (!isSuccessfulPayment(p)) {
                updatePaymentStatus(p.getId(), "PAID", "CASHFREE_VERIFIED", "Manual Verification Success", p.getAmount(), null);
                result.put("updated", true);
                result.put("message", "Payment verified and database updated to PAID.");
            } else {
                result.put("updated", false);
                result.put("message", "Payment was already marked as PAID.");
            }
        } else if ("FAILED".equalsIgnoreCase(cfOrder.getOrder_status()) || "CANCELLED".equalsIgnoreCase(cfOrder.getOrder_status())) {
            Payment p = paymentRepository.findTopByPaymentGatewayIdOrderByCreatedAtDesc(orderId).orElse(null);
            if (p != null && p.getStatus() == Payment.Status.PENDING) {
                updatePaymentStatus(p.getId(), "FAILED", "CASHFREE_VERIFIED", "Gateway reports failure: " + cfOrder.getOrder_status(), null, null);
                result.put("updated", true);
                result.put("message", "Payment marked as FAILED in database.");
            } else {
                result.put("updated", false);
                result.put("message", "Payment status is " + cfOrder.getOrder_status());
            }
        } else {
            result.put("updated", false);
            result.put("message", "Gateway reports status: " + cfOrder.getOrder_status());
        }

        return result;
    }

    public com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse getCashfreeOrderRaw(String orderId) {
        return cashfreeService.getOrder(orderId);
    }

    public Map<String, String> fetchCashfreeOrder(String orderId) {
        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfResponse = cashfreeService.getOrder(orderId);
        Map<String, String> result = new HashMap<>();
        result.put("order_id", orderId);
        result.put("payment_session_id", cfResponse.getPayment_session_id());
        return result;
    }

    @Transactional
    public Map<String, String> generatePaymentLink(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        Lead lead = leadRepository.findById(payment.getLeadId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        String orderId = "REMI_" + payment.getId() + "_" + System.currentTimeMillis();

        // Refactored Cashfree request builder
        com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest request = buildCashfreeRequest(lead, orderId, payment.getAmount());

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfResponse = cashfreeService.createOrder(request);

        if (cfResponse == null || cfResponse.getPayment_session_id() == null) {
            throw new RuntimeException("Gateway Order Initiation Failed. Verify details.");
        }

        payment.setPaymentGatewayId(orderId);
        paymentRepository.save(payment);

        Map<String, String> result = new HashMap<>();
        result.put("payment_url", frontendUrl + "/payment-instruction/" + orderId);
        result.put("order_id", orderId);
        return result;
    }

    public void sendInstallmentReminder(Payment payment) {
        Lead lead = leadRepository.findById(payment.getLeadId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        Map<String, String> linkResult = generatePaymentLink(payment.getId());
        String paymentUrl = linkResult.get("payment_url");

        String dueDateStr = payment.getDueDate() != null ? payment.getDueDate().toLocalDate().toString() : "N/A";
        
        // Delegate directly to Thymeleaf template in MailService
        mailService.sendOverdueReminder(lead.getEmail(), lead.getName(), payment.getAmount(), dueDateStr);

        payment.setReminderSent(true);
        paymentRepository.save(payment);

        leadTaskService.createLeadTask(lead, LocalDateTime.now(), "Installment Reminder Sent - ₹" + payment.getAmount(), "EMI_COLLECTION");
    }

    public void sendSameDayInstallmentReminder(Payment payment) {
        Lead lead = leadRepository.findById(payment.getLeadId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        Map<String, String> linkResult = generatePaymentLink(payment.getId());
        String paymentUrl = linkResult.get("payment_url");

        String dueDateStr = payment.getDueDate() != null ? payment.getDueDate().toLocalDate().toString() : "N/A";
        
        mailService.sendOverdueReminder(lead.getEmail(), lead.getName(), payment.getAmount(), dueDateStr);

        payment.setDueDateReminderSent(true);
        paymentRepository.save(payment);

        leadTaskService.createLeadTask(lead, LocalDateTime.now(), "Same-Day Installment Reminder Sent - ₹" + payment.getAmount(), "EMI_COLLECTION");
    }

    @Transactional
    public void markAsPaid(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        lead.setStatus(STATUS_CONVERTED);
        leadRepository.save(lead);

        log.info("Lead {} manually marked as PAID/CONVERTED", lead.getEmail());
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getTeamLeaders() {
        return userRepository.findByRoleName("TEAM_LEADER").stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentDTO generateInvoice(Long leadId) {
        return paymentRepository.findByLeadIdAndStatus(leadId, Payment.Status.PAID).stream()
                .max(Comparator.comparing(Payment::getCreatedAt))
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("No successful payment found for lead: " + leadId));
    }

    @Transactional(readOnly = true)
    public PaymentDTO generateInvoiceByPaymentId(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return convertToDTO(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> getFilteredPaymentHistory(Long managerId, Long userId, Long tlId, Long associateId,
            LocalDateTime start, LocalDateTime end, String status) {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> targetUserIds = securityService.getAllowedUserIds(requester);

        if (userId != null) {
            securityService.validateAccess(requester, userId);
            targetUserIds = java.util.Collections.singleton(userId);
        } else if (associateId != null) {
            securityService.validateAccess(requester, associateId);
            targetUserIds = java.util.Collections.singleton(associateId);
        } else if (tlId != null) {
            securityService.validateAccess(requester, tlId);
            targetUserIds = new java.util.HashSet<>(userRepository.findSubordinateIds(tlId));
            targetUserIds.add(tlId);
        } else if (managerId != null) {
            securityService.validateAccess(requester, managerId);
            targetUserIds = new java.util.HashSet<>(userRepository.findSubordinateIds(managerId));
            targetUserIds.add(managerId);
        }

        boolean isGlobalAdmin = securityService.isAdmin(requester) && managerId == null && tlId == null
                && associateId == null && userId == null;

        Payment.Status pStatus = (status != null && !status.isEmpty()) ? Payment.Status.valueOf(status.toUpperCase()) : null;

        if (isGlobalAdmin) {
            return paymentRepository.findFiltered(null, start, end, pStatus).stream()
                    .map(this::convertToDTO).collect(Collectors.toList());
        } else {
            return paymentRepository
                    .findFilteredByUserHierarchy(new java.util.ArrayList<>(targetUserIds), start, end, pStatus).stream()
                    .map(this::convertToDTO).collect(Collectors.toList());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> getFilteredPaymentHistoryForTL(String username, LocalDateTime start, LocalDateTime end,
            String status, Long userId) {
        User tl = userRepository.findByEmail(username).orElseThrow();
        return getFilteredPaymentHistory(null, userId, tl.getId(), null, start, end, status);
    }

    @Transactional
    public Map<String, String> createPaymentLink(Long leadId, BigDecimal initialAmount, BigDecimal totalAmount,
            PaymentSplitRequest splitRequest) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        User requester = securityService.getCurrentUser();
        if (lead.getAssignedTo() != null) {
            securityService.validateAccess(requester, lead.getAssignedTo().getId());
        }

        BigDecimal minAmount = new BigDecimal("500");
        if (lead.getCourse() != null && lead.getCourse().getMinTokenAmount() != null) {
            minAmount = lead.getCourse().getMinTokenAmount();
        }

        if (initialAmount.compareTo(minAmount) < 0) {
            throw new InvalidRequestException("Minimum payment amount is ₹" + minAmount);
        }

        Payment saved = paymentRepository.save(
                Payment.builder()
                        .leadId(leadId)
                        .amount(initialAmount)
                        .totalAmount(totalAmount != null ? totalAmount : initialAmount)
                        .status(Payment.Status.PENDING)
                        .paymentType(splitRequest != null ? TYPE_EMI_INSTALLMENT : "FULL")
                        .build());

        if (splitRequest != null) {
            splitPayment(saved.getId(), splitRequest);
        }

        studentFeeService.syncStudentFee(lead, BigDecimal.ZERO, totalAmount, null, null);

        String gatewayOrderId = "REMI_" + saved.getId() + "_" + System.currentTimeMillis();

        // Reusable Cashfree request builder
        com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest cfRequest = buildCashfreeRequest(lead, gatewayOrderId, initialAmount);

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfResponse = cashfreeService.createOrder(cfRequest);

        if (cfResponse == null || cfResponse.getPayment_session_id() == null) {
            throw new RuntimeException("Cashfree order failed. Check credentials. Response: " + cfResponse);
        }

        String sessionId = cfResponse.getPayment_session_id();
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("Invalid Cashfree session received.");
        }

        saved.setPaymentGatewayId(gatewayOrderId);
        paymentRepository.save(saved);

        Map<String, String> response = new HashMap<>();
        response.put("order_id", gatewayOrderId);
        response.put("payment_session_id", sessionId);
        response.put("paymentSessionId", sessionId);
        response.put("payment_url", frontendUrl + "/payment-instruction/" + gatewayOrderId);

        return response;
    }

    @Transactional
    public PaymentDTO updatePaymentStatus(Long paymentId, Map<String, String> payload) {
        String status = payload.get("status");
        String method = payload.get("method");
        String note = payload.get("note");
        BigDecimal amount = payload.containsKey("actualPaidAmount") ? new BigDecimal(payload.get("actualPaidAmount")) : null;
        String nextDue = payload.get("nextDueDate");

        return updatePaymentStatus(paymentId, status, method, note, amount, nextDue);
    }

    @Transactional
    public PaymentDTO updatePaymentStatus(Long paymentId, String status, String method, String note,
            BigDecimal actualPaidAmount, String nextDueDateStr) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User currentUser = securityService.getCurrentUser();

        if (status != null) {
            payment.setStatus(Payment.Status.valueOf(status.toUpperCase()));
        }
        if (method != null) {
            payment.setPaymentMethod(method);
        }
        payment.setNote(note);
        payment.setUpdatedBy(currentUser);
        payment.setUpdatedAt(LocalDateTime.now());

        if (payment.getStatus() == Payment.Status.PAID) {
            processSuccessfulPayment(payment, actualPaidAmount, nextDueDateStr);
        }

        return convertToDTO(paymentRepository.save(payment));
    }

    private void processSuccessfulPayment(Payment payment, BigDecimal actualPaidAmount, String nextDueDateStr) {
        Lead lead = leadRepository.findById(payment.getLeadId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead linked to payment not found"));

        if (actualPaidAmount != null && actualPaidAmount.compareTo(BigDecimal.ZERO) > 0
                && actualPaidAmount.compareTo(payment.getAmount()) < 0) {
            handlePartialPayment(payment, lead, actualPaidAmount, nextDueDateStr);
        } else {
            handleFullPayment(payment, lead);
        }
        lead.setStatus(STATUS_CONVERTED);
        leadRepository.save(lead);
    }

    private void handlePartialPayment(Payment payment, Lead lead, BigDecimal paidAmount, String nextDueDateStr) {
        BigDecimal remaining = payment.getAmount().subtract(paidAmount);
        payment.setAmount(paidAmount);
        payment.setPaymentType("INSTALLMENT");

        LocalDateTime nextDue = parseDate(nextDueDateStr);
        if (nextDue != null) {
            Payment nextInstallment = Payment.builder()
                    .leadId(payment.getLeadId())
                    .amount(remaining)
                    .totalAmount(payment.getTotalAmount())
                    .status(Payment.Status.PENDING)
                    .paymentType(TYPE_EMI_INSTALLMENT)
                    .dueDate(nextDue)
                    .build();
            paymentRepository.save(nextInstallment);

            lead.setFollowUpDate(nextDue);
            lead.setFollowUpRequired(true);
            lead.setFollowUpType("EMI_COLLECTION");
            leadRepository.save(lead);

            leadTaskService.createLeadTask(lead, nextDue, "EMI Collection - Remainder", "EMI_COLLECTION");
        }
        studentFeeService.syncStudentFee(lead, paidAmount, payment.getTotalAmount(), null, nextDue);
    }

    private void handleFullPayment(Payment payment, Lead lead) {
        // Complete sales-related follow-ups
        List<LeadTask> followUps = leadTaskRepository.findByLeadId(lead.getId()).stream()
                .filter(t -> t.getStatus() == LeadTask.TaskStatus.PENDING)
                .filter(t -> !"EMI_COLLECTION".equalsIgnoreCase(t.getTaskType()))
                .collect(Collectors.toList());
        followUps.forEach(t -> t.setStatus(LeadTask.TaskStatus.COMPLETED));
        leadTaskRepository.saveAll(followUps);

        // Complete the specific EMI task
        if (payment.getDueDate() != null) {
            leadTaskRepository.findByLeadId(lead.getId()).stream()
                    .filter(t -> t.getStatus() == LeadTask.TaskStatus.PENDING)
                    .filter(t -> "EMI_COLLECTION".equalsIgnoreCase(t.getTaskType()))
                    .filter(t -> t.getDueDate() != null && t.getDueDate().toLocalDate().isEqual(payment.getDueDate().toLocalDate()))
                    .findFirst()
                    .ifPresent(t -> {
                        t.setStatus(LeadTask.TaskStatus.COMPLETED);
                        leadTaskRepository.save(t);
                    });
        }

        // Refactored to delegate directly to Thymeleaf template in MailService
        mailService.sendAdmissionSuccess(lead.getEmail(), lead.getName(), payment.getPaymentGatewayId(), payment.getAmount(), payment.getPaymentMethod());
        studentFeeService.syncStudentFee(lead, payment.getAmount(), payment.getTotalAmount(), null, null);
    }

    // --- MODULAR MANUAL PAYMENT HANDLING ---

    @Transactional
    public PaymentDTO recordManualPayment(com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO data, String receiptUrl) {
        Long leadId = data.getLeadId();
        BigDecimal amount = data.getAmount();
        BigDecimal totalAmount = data.getTotalAmount() != null ? data.getTotalAmount() : amount;

        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        User requester = securityService.getCurrentUser();

        if (lead.getAssignedTo() != null) {
            securityService.validateAccess(requester, lead.getAssignedTo().getId());
        }

        String utr = data.getUtr();
        validateUtrUniqueness(utr);

        boolean isManagerOrAdmin = securityService.isAdmin(requester) || securityService.isManager(requester);
        BigDecimal discount = data.getDiscount() != null ? data.getDiscount() : BigDecimal.ZERO;
        String paymentMethod = data.getPaymentMethod();
        String note = data.getNote();
        
        // All manual payment verifications (CASH, UPI, BANK TRANSFER) recorded by Associates or Team Leaders require Manager/Admin approval.
        Payment.Status targetStatus = isManagerOrAdmin ? Payment.Status.PAID : Payment.Status.PENDING_APPROVAL;
        
        // Modular helper to query exact installment or first fulfillable slot
        Payment payment = null;
        if (data.getInstallmentId() != null) {
            payment = paymentRepository.findById(data.getInstallmentId()).orElse(null);
        }
        if (payment == null) {
            payment = findFulfillableInstallment(leadId);
        }

        if (payment != null) {
            // Modular helper to handle partial EMI fulfillments
            handlePartialManualPayment(payment, amount, totalAmount, lead, data.getNextDueDate());
            
            payment.setAmount(amount);
            payment.setTotalAmount(totalAmount);
            payment.setStatus(targetStatus);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentGatewayId(utr != null && !utr.isBlank() ? utr : "MANUAL_" + System.currentTimeMillis());
            payment.setReceiptUrl(receiptUrl);
            payment.setNote(note);
            payment.setUpdatedBy(requester);
        } else {
            String businessName = data.getBusinessName() != null ? data.getBusinessName() : CONST_BUSINESS_NAME;
            String businessAddress = data.getBusinessAddress() != null ? data.getBusinessAddress() : CONST_BUSINESS_ADDRESS;
            String businessContact = data.getBusinessContact() != null ? data.getBusinessContact() : CONST_BUSINESS_CONTACT;
            String businessEmail = data.getBusinessEmail() != null ? data.getBusinessEmail() : CONST_BUSINESS_EMAIL;
            String taxId = data.getTaxId() != null ? data.getTaxId() : CONST_TAX_ID;

            payment = Payment.builder()
                .leadId(leadId)
                .amount(amount)
                .totalAmount(totalAmount)
                .status(targetStatus)
                .paymentType(data.getPaymentType() != null ? data.getPaymentType() : TYPE_EMI_INSTALLMENT)
                .paymentMethod(paymentMethod)
                .paymentGatewayId((utr != null && !utr.isBlank()) ? utr : "MANUAL_" + System.currentTimeMillis())
                .receiptUrl(receiptUrl)
                .note(note)
                .updatedBy(requester)
                .businessName(businessName)
                .businessAddress(businessAddress)
                .businessContact(businessContact)
                .businessEmail(businessEmail)
                .taxId(taxId)
                .build();
        }

        Payment saved = paymentRepository.save(payment);
        log.info("Primary Manual Payment Saved: ID={}, Amount={}, Status={}", saved.getId(), saved.getAmount(), saved.getStatus());

        List<com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO.InstallmentDetail> installments = data.getInstallments();
        if (installments != null && data.getInstallmentId() == null) {
            paymentRepository.findByLeadId(leadId).stream()
                .filter(p -> p.getStatus() == Payment.Status.PENDING && TYPE_EMI_INSTALLMENT.equals(p.getPaymentType()) && !p.getId().equals(saved.getId()))
                .forEach(p -> paymentRepository.delete(p));
            paymentRepository.flush();
            
            // Modular helper to process batch manual installments
            processManualInstallments(installments, leadId, totalAmount, paymentMethod, requester, false);
        }

        // Modular helper to safely parse future installment date structures
        LocalDateTime nextDueToUse = parseDate(data.getNextDueDate());
        if (nextDueToUse == null && installments != null) {
            nextDueToUse = installments.stream()
                .map(com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO.InstallmentDetail::getDueDate)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .map(this::parseDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        }

        BigDecimal paidToSync = (targetStatus == Payment.Status.PAID) ? amount : BigDecimal.ZERO;
        studentFeeService.syncStudentFee(lead, paidToSync, totalAmount, discount, nextDueToUse);

        if (targetStatus == Payment.Status.PAID) {
            processSuccessfulPayment(saved, amount, data.getNextDueDate());
        } else {
            log.info("Manual Payment requires Approval. Lead conversion pending.");
        }

        return convertToDTO(saved);
    }

    @Transactional
    public void approvePayment(Long paymentId, User requester) {
        if (!securityService.isAdmin(requester) && !securityService.isManager(requester)) {
            throw new AccessDeniedException("Only Managers and Admins can approve manual payments.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != Payment.Status.PENDING_APPROVAL) {
            throw new InvalidRequestException("This payment is not awaiting approval.");
        }

        payment.setStatus(Payment.Status.PAID);
        payment.setUpdatedBy(requester);
        paymentRepository.save(payment);

        processSuccessfulPayment(payment, payment.getAmount(), null);
        
        log.info("Manager {} Approved Payment for Lead ID: {}", requester.getEmail(), payment.getLeadId());
    }

    @Transactional
    public void rejectPayment(Long paymentId, String reason, User requester) {
        if (!securityService.isAdmin(requester) && !securityService.isManager(requester)) {
            throw new AccessDeniedException("Only Managers and Admins can reject manual payments.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != Payment.Status.PENDING_APPROVAL) {
            throw new InvalidRequestException("This payment is not awaiting approval.");
        }

        payment.setStatus(Payment.Status.REJECTED);
        payment.setNote(payment.getNote() + " | REJECTED: " + reason);
        payment.setUpdatedBy(requester);
        paymentRepository.save(payment);

        int instNum = getInstallmentNumber(payment);
        String granularStatus = "REJECTED_INSTALLMENT_" + instNum;

        studentFeeRepository.findByLeadId(payment.getLeadId()).ifPresent(fee -> {
            fee.setPaymentStatus(granularStatus);
            studentFeeRepository.save(fee);
        });

        leadRepository.findById(payment.getLeadId()).ifPresent(lead -> {
            log.info("Transitioning Lead {} status from {} to {} due to rejection", lead.getId(), lead.getStatus(), granularStatus);
            lead.setStatus(granularStatus);
            leadRepository.save(lead);
        });
    }

    private int getInstallmentNumber(Payment payment) {
        List<Payment> all = paymentRepository.findByLeadId(payment.getLeadId());
        all.sort(Comparator.comparing(p -> p.getCreatedAt() != null ? p.getCreatedAt() : LocalDateTime.MIN));
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(payment.getId())) {
                return i + 1;
            }
        }
        return 1;
    }

    private PaymentDTO convertToDTO(Payment payment) {
        Lead lead = leadRepository.findById(payment.getLeadId()).orElse(null);
        PaymentDTO dto = PaymentDTO.fromEntity(payment, lead);

        studentFeeRepository.findByLeadId(payment.getLeadId()).ifPresent(fee -> {
            dto.setTotalPackageAmount(fee.getTotalAmount());
            dto.setPaidAmountSoFar(fee.getPaidAmount());
            dto.setBalanceDue(fee.getBalanceAmount());
            dto.setNextInstallmentDate(fee.getNextDueDate());
        });
        return dto;
    }



    @Transactional
    public void splitPayment(Long paymentId, PaymentSplitRequest splitRequest) {
        Payment original = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (original.getStatus() == Payment.Status.PAID) {
            throw new InvalidRequestException("Cannot split a completed payment");
        }

        if (splitRequest.getInstallments() != null && splitRequest.getInstallments().size() > 5) {
            throw new InvalidRequestException("Maximum of 5 EMI installments allowed per lead protocol.");
        }

        Lead lead = leadRepository.findById(original.getLeadId()).orElse(null);

        for (int i = 0; i < splitRequest.getInstallments().size(); i++) {
            PaymentSplitRequest.InstallmentPart part = splitRequest.getInstallments().get(i);
            LocalDateTime dDate = parseDate(part.getDueDate());

            if (i == 0) {
                original.setAmount(part.getAmount());
                original.setDueDate(dDate);
                paymentRepository.save(original);
            } else {
                paymentRepository.save(Payment.builder()
                        .leadId(original.getLeadId())
                        .amount(part.getAmount())
                        .totalAmount(original.getTotalAmount())
                        .status(Payment.Status.PENDING)
                        .paymentType(TYPE_EMI_INSTALLMENT)
                        .dueDate(dDate)
                        .build());
            }
            if (lead != null && dDate != null) {
                leadTaskService.createLeadTask(lead, dDate, "Split EMI Part " + (i + 1), "EMI_COLLECTION");
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentFeeStructure(Long leadId) {
        return studentFeeService.getStudentFeeStructure(leadId);
    }

    // --- REFACTORED PRIVATE UTILITY HELPERS ---

    private String sanitizePhone(String mobile) {
        String cleanPhone = mobile != null ? mobile.replaceAll("[^0-9]", "") : "";
        if (cleanPhone.length() > 10) {
            cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
        }
        if (cleanPhone.length() < 10) {
            cleanPhone = "9999999999";
        }
        return cleanPhone;
    }

    private com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest buildCashfreeRequest(
            Lead lead, String orderId, BigDecimal amount) {
        String cleanPhone = sanitizePhone(lead.getMobile());
        String cleanEmail = (lead.getEmail() != null && !lead.getEmail().isBlank()) ? lead.getEmail() : "test@example.com";
        String cleanName = lead.getName() != null && !lead.getName().isBlank() ? lead.getName() : "Customer";
        
        String expiryTime = java.time.ZonedDateTime.now().plusHours(48)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        
        return com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.builder()
                .order_id(orderId)
                .order_amount(amount)
                .order_currency("INR")
                .order_expiry_time(expiryTime)
                .customer_details(com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.CustomerDetails.builder()
                        .customer_id("CUST_" + lead.getId())
                        .customer_name(cleanName)
                        .customer_email(cleanEmail)
                        .customer_phone(cleanPhone)
                        .build())
                .order_meta(com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.OrderMeta.builder()
                        .return_url(frontendUrl + "/payment-status/" + orderId)
                        .notify_url(webhookUrl != null && webhookUrl.startsWith("https://") ? webhookUrl : null)
                        .build())
                .build();
    }

    private boolean isSuccessfulPayment(Payment p) {
        if (p == null || p.getStatus() == null) return false;
        Payment.Status s = p.getStatus();
        return s == Payment.Status.PAID || s == Payment.Status.SUCCESS || s == Payment.Status.COMPLETED;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            String cleanDate = dateStr.trim();
            if (cleanDate.contains("T")) {
                if (cleanDate.endsWith("Z")) {
                    return java.time.ZonedDateTime.parse(cleanDate).toLocalDateTime();
                }
                try {
                    return java.time.OffsetDateTime.parse(cleanDate).toLocalDateTime();
                } catch (Exception ex) {
                    if (cleanDate.length() == 16) {
                        cleanDate += ":00";
                    }
                    if (cleanDate.contains("+")) {
                        cleanDate = cleanDate.substring(0, cleanDate.indexOf('+'));
                    } else if (cleanDate.contains("-") && cleanDate.lastIndexOf('-') > cleanDate.indexOf('T')) {
                        cleanDate = cleanDate.substring(0, cleanDate.lastIndexOf('-'));
                    }
                    return LocalDateTime.parse(cleanDate);
                }
            } else {
                if (cleanDate.contains("/")) {
                    try {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
                        return LocalDateTime.parse(cleanDate, formatter);
                    } catch (Exception e1) {
                        try {
                            java.time.format.DateTimeFormatter formatter2 = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
                            return java.time.LocalDate.parse(cleanDate, formatter2).atTime(10, 0);
                        } catch (Exception e2) {
                            // let fallback happen
                        }
                    }
                }
                if (cleanDate.length() == 10) {
                    return java.time.LocalDate.parse(cleanDate).atTime(10, 0);
                }
                cleanDate += "T10:00:00";
                return LocalDateTime.parse(cleanDate);
            }
        } catch (Exception e) {
            log.warn(">>> Failed to parse date '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }

    private void validateUtrUniqueness(String utr) {
        if (utr != null && !utr.isBlank()) {
            if (paymentRepository.existsByPaymentGatewayIdAndStatusNot(utr, Payment.Status.REJECTED)) {
                log.error("Duplicate Payment Protocol Detected: Active UTR {} already exists in system.", utr);
                throw new InvalidRequestException("Accounting Violation: This UTR (" + utr + ") has already been recorded for another active payment.");
            }
        }
    }

    private Payment findFulfillableInstallment(Long leadId) {
        return paymentRepository.findByLeadId(leadId).stream()
                .filter(p -> (p.getStatus() == Payment.Status.PENDING || p.getStatus() == Payment.Status.REJECTED || p.getStatus() == Payment.Status.OVERDUE) 
                        && TYPE_EMI_INSTALLMENT.equals(p.getPaymentType()))
                .sorted(Comparator.comparing(p -> p.getDueDate() != null ? p.getDueDate() : LocalDateTime.MAX))
                .findFirst()
                .orElse(null);
    }

    private void handlePartialManualPayment(Payment payment, BigDecimal paidAmount, BigDecimal totalAmount, Lead lead, String nextDueDateStr) {
        BigDecimal originalAmount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        if (paidAmount.compareTo(originalAmount) < 0) {
            BigDecimal remainder = originalAmount.subtract(paidAmount);
            
            LocalDateTime remainderDueDate = parseDate(nextDueDateStr);
            if (remainderDueDate == null) {
                remainderDueDate = payment.getDueDate() != null ? payment.getDueDate().plusDays(7) : LocalDateTime.now().plusDays(7);
            }
            Payment remainderInst = Payment.builder()
                    .leadId(payment.getLeadId())
                    .amount(remainder)
                    .totalAmount(totalAmount)
                    .status(Payment.Status.PENDING)
                    .paymentType(TYPE_EMI_INSTALLMENT)
                    .dueDate(remainderDueDate)
                    .build();
            paymentRepository.save(remainderInst);
            
            leadTaskService.createLeadTask(lead, remainderDueDate, "EMI Collection - Remainder (Partial)", "EMI_COLLECTION");
        } else if (paidAmount.compareTo(originalAmount) > 0) {
            BigDecimal excess = paidAmount.subtract(originalAmount);
            
            List<Payment> pendingInstallments = paymentRepository.findByLeadId(payment.getLeadId()).stream()
                    .filter(p -> p.getStatus() == Payment.Status.PENDING && TYPE_EMI_INSTALLMENT.equals(p.getPaymentType()) && !p.getId().equals(payment.getId()))
                    .sorted(Comparator.comparing(p -> p.getDueDate() != null ? p.getDueDate() : LocalDateTime.MAX))
                    .collect(Collectors.toList());
            
            for (Payment pending : pendingInstallments) {
                if (excess.compareTo(BigDecimal.ZERO) <= 0) break;
                
                BigDecimal pendingAmount = pending.getAmount() != null ? pending.getAmount() : BigDecimal.ZERO;
                if (excess.compareTo(pendingAmount) >= 0) {
                    excess = excess.subtract(pendingAmount);
                    paymentRepository.delete(pending);
                } else {
                    pending.setAmount(pendingAmount.subtract(excess));
                    paymentRepository.save(pending);
                    excess = BigDecimal.ZERO;
                }
            }
        }
    }

    private void processManualInstallments(List<com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO.InstallmentDetail> installments, Long leadId, BigDecimal totalPackageAmount, String paymentMethod, User requester, boolean isExistingEmiPlan) {
        
        for (com.lms.www.leadmanagement.dto.ManualPaymentRequestDTO.InstallmentDetail instData : installments) {
            try {
                BigDecimal instAmount = instData.getAmount();
                if (instAmount == null || instAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

                String dueDateStr = instData.getDueDate();
                LocalDateTime dueDate = parseDate(dueDateStr);

                Payment inst = Payment.builder()
                        .leadId(leadId)
                        .amount(instAmount)
                        .totalAmount(totalPackageAmount)
                        .status(Payment.Status.PENDING)
                        .paymentType(TYPE_EMI_INSTALLMENT)
                        .dueDate(dueDate)
                        .paymentMethod(paymentMethod)
                        .updatedBy(requester)
                        .businessName(instData.getBusinessName() != null ? instData.getBusinessName() : CONST_BUSINESS_NAME)
                        .businessAddress(instData.getBusinessAddress() != null ? instData.getBusinessAddress() : CONST_BUSINESS_ADDRESS)
                        .businessContact(instData.getBusinessContact() != null ? instData.getBusinessContact() : CONST_BUSINESS_CONTACT)
                        .businessEmail(instData.getBusinessEmail() != null ? instData.getBusinessEmail() : CONST_BUSINESS_EMAIL)
                        .taxId(instData.getTaxId() != null ? instData.getTaxId() : CONST_TAX_ID)
                        .build();
                paymentRepository.saveAndFlush(inst);
            } catch (Exception e) {
                log.error(">>> Error processing manual installment: {}", e.getMessage(), e);
            }
        }
    }
}