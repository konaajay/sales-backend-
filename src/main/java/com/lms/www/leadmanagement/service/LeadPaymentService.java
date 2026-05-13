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

    private final LeadRepository leadRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final LeadTaskRepository leadTaskRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final MailService mailService;
    private final SecurityService securityService;
    private final CashfreeService cashfreeService;

    @Value("${cashfree.webhook.url}")
    private String webhookUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public Map<String, String> createCashfreeOrder(Long leadId, BigDecimal amount, String type, List<Map<String, Object>> plannedInstallments, BigDecimal totalAmount, BigDecimal discount) {
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

        // Fix: totalPlanned already includes 'amount' from line 59
        BigDecimal totalAccounted = totalPlanned;
        if (finalSettlement.compareTo(BigDecimal.ZERO) > 0 && totalAccounted.compareTo(finalSettlement) != 0) {
            throw new InvalidRequestException("Accounting Protocol Violation: Total Commitment (\u20B9" + totalAccounted + ") does not match Final Settlement (\u20B9" + finalSettlement + "). Remaining to plan: \u20B9" + finalSettlement.subtract(amount));
        }
        // ------------------------------------

        String orderId = "ORDER_" + leadId + "_" + System.currentTimeMillis();
        
        // Set order expiry to 48 hours from now with timezone offset
        String expiryTime = java.time.ZonedDateTime.now().plusHours(48)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // Sanitize phone number for Cashfree (must be 10 digits)
        String cleanPhone = lead.getMobile() != null ? lead.getMobile().replaceAll("[^0-9]", "") : "";
        if (cleanPhone.length() > 10) {
            cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
        }

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest request = 
            com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.builder()
                .order_id(orderId)
                .order_amount(amount)
                .order_currency("INR")
                .order_expiry_time(expiryTime)
                .customer_details(com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.CustomerDetails.builder()
                        .customer_id("CUST_" + leadId)
                        .customer_name(lead.getName())
                        .customer_email(lead.getEmail())
                        .customer_phone(cleanPhone)
                        .build())
                .order_meta(com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.OrderMeta.builder()
                        .return_url(frontendUrl + "/payment-status/" + orderId)
                        .notify_url(webhookUrl != null && webhookUrl.startsWith("https://") ? webhookUrl : null)
                        .build())
                .build();

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfResponse = cashfreeService.createOrder(request);

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
        paymentRepository.deleteByLeadIdAndStatusAndPaymentType(leadId, Payment.Status.PENDING, "EMI_INSTALLMENT");

        if (plannedInstallments != null && !plannedInstallments.isEmpty()) {
            for (Map<String, Object> inst : plannedInstallments) {
                BigDecimal instAmount = new BigDecimal(inst.get("amount").toString());
                totalCommitment = totalCommitment.add(instAmount);
                
                String dueStr = (String) inst.get("dueDate");
                LocalDateTime dueDate = null;
                if (dueStr != null && !dueStr.isBlank()) {
                    try {
                        dueDate = LocalDateTime.parse(dueStr.contains("T") ? dueStr : dueStr + "T10:00:00");
                    } catch (Exception e) {
                        log.warn(">>> Failed to parse due date: {}. Using null.", dueStr);
                    }
                }
                if (firstInstallmentDate == null) firstInstallmentDate = dueDate;

                paymentRepository.save(Payment.builder()
                        .leadId(leadId)
                        .amount(instAmount)
                        .status(Payment.Status.PENDING)
                        .paymentType("EMI_INSTALLMENT")
                        .dueDate(dueDate)
                        .note("Planned during initial link generation")
                        .build());
                
                if (dueDate != null) {
                    createLeadTask(lead, dueDate, "EMI Collection - Planned", "EMI_COLLECTION");
                }
            }
        }

        // Initialize/Sync Student Fee Structure so it shows in dashboard immediately
        syncStudentFee(lead, BigDecimal.ZERO, totalAmount != null ? totalAmount : totalCommitment, discount, firstInstallmentDate);

        // AUTO-EMAIL student the payment request link
        sendPaymentRequestEmail(lead, amount, orderId);

        Map<String, String> result = new HashMap<>();
        result.put("payment_session_id", cfResponse.getPayment_session_id());
        result.put("order_id", orderId);
        result.put("payment_url", frontendUrl + "/payment-instruction/" + orderId);
        return result;
    }

    @Transactional
    public Map<String, Object> verifyAndUpdatePayment(String orderId) {
        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfOrder = cashfreeService.getOrder(orderId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("gatewayStatus", cfOrder.getOrder_status());

        if ("PAID".equalsIgnoreCase(cfOrder.getOrder_status())) {
            Payment p = paymentRepository.findByPaymentGatewayId(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment record not found in DB"));

            if (p.getStatus() != Payment.Status.PAID && p.getStatus() != Payment.Status.SUCCESS) {
                updatePaymentStatus(p.getId(), "PAID", "CASHFREE_VERIFIED", "Manual Verification Success", p.getAmount(), null);
                result.put("updated", true);
                result.put("message", "Payment verified and database updated to PAID.");
            } else {
                result.put("updated", false);
                result.put("message", "Payment was already marked as PAID.");
            }
        } else if ("FAILED".equalsIgnoreCase(cfOrder.getOrder_status()) || "CANCELLED".equalsIgnoreCase(cfOrder.getOrder_status())) {
            Payment p = paymentRepository.findByPaymentGatewayId(orderId).orElse(null);
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
        result.put("payment_session_id", cfResponse.getPayment_session_id());
        result.put("order_id", orderId);
        return result;
    }

    private void sendPaymentRequestEmail(Lead lead, BigDecimal amount, String orderId) {
        String subject = "Enrollment Action Required - Payment Link for " + lead.getName();
        String paymentUrl = frontendUrl + "/payment-instruction/" + orderId;

        String body = String.format(
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 12px; overflow: hidden;'>" +
                "  <div style='background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); color: white; padding: 30px; text-align: center;'>" +
                "    <h2 style='margin: 0; text-transform: uppercase; letter-spacing: 2px;'>Enrollment Protocol</h2>" +
                "    <p style='margin: 10px 0 0; opacity: 0.9;'>Action required to secure your seat</p>" +
                "  </div>" +
                "  <div style='padding: 40px; line-height: 1.6; color: #1e293b;'>" +
                "    <p>Hello <strong>%s</strong>,</p>" +
                "    <p>To finalize your enrollment and secure your registration, please complete the initial commitment payment of <strong>₹%s</strong>.</p>" +
                "    " +
                "    <div style='margin: 30px 0; text-align: center;'>" +
                "      <a href='%s' style='background-color: #3b82f6; color: white; padding: 16px 32px; border-radius: 8px; text-decoration: none; font-weight: bold; display: inline-block; box-shadow: 0 4px 6px rgba(59, 130, 246, 0.2);'>PROCEED TO SECURE CHECKOUT</a>" +
                "    </div>" +
                "    " +
                "    <p style='font-size: 14px; color: #64748b;'>If the button doesn't work, copy and paste this link: <br/> %s</p>" +
                "    " +
                "    <div style='border-top: 1px solid #f1f5f9; margin-top: 30px; padding-top: 20px; font-size: 12px; color: #94a3b8;'>" +
                "      Order reference: %s<br/>" +
                "      This link is secure and valid for 48 hours." +
                "    </div>" +
                "  </div>" +
                "</div>",
                lead.getName(), amount, paymentUrl, paymentUrl, orderId);

        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            try {
                mailService.sendEmail(lead.getEmail(), subject, body);
            } catch (Exception e) {
                log.error(">>> Email Delivery Failed: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public Map<String, String> generatePaymentLink(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        String orderId = "REMI_" + payment.getId() + "_" + System.currentTimeMillis();
        
        // Set order expiry to 48 hours from now with timezone offset
        String expiryTime = java.time.ZonedDateTime.now().plusHours(48)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest request = 
            com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.builder()
                .order_id(orderId)
                .order_amount(payment.getAmount())
                .order_currency("INR")
                .order_expiry_time(expiryTime)
                .customer_details(com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.CustomerDetails.builder()
                        .customer_id("CUST_" + payment.getLeadId())
                        .customer_name("Student") // Lead name will be updated if possible
                        .build())
                .order_meta(com.lms.www.leadmanagement.dto.payment.CashfreeOrderRequest.OrderMeta.builder()
                        .return_url(frontendUrl + "/payment-status/" + orderId)
                        .notify_url(webhookUrl != null && webhookUrl.startsWith("https://") ? webhookUrl : null)
                        .build())
                .build();

        // Get lead name for better request if available
        leadRepository.findById(payment.getLeadId()).ifPresent(l -> {
            request.getCustomer_details().setCustomer_name(l.getName());
            request.getCustomer_details().setCustomer_email(l.getEmail());
            
            // Sanitize phone number for Cashfree (must be 10 digits)
            String cleanPhone = l.getMobile() != null ? l.getMobile().replaceAll("[^0-9]", "") : "";
            if (cleanPhone.length() > 10) {
                cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
            }
            request.getCustomer_details().setCustomer_phone(cleanPhone);
        });

        com.lms.www.leadmanagement.dto.payment.CashfreeOrderResponse cfResponse = cashfreeService.createOrder(request);

        // Update existing payment with the new Gateway ID
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
        
        String subject = "Payment Reminder: Installment Due for " + lead.getName();
        
        StudentFee fee = studentFeeRepository.findByLeadId(lead.getId()).orElse(null);
        BigDecimal balance = (fee != null) ? fee.getBalanceAmount() : BigDecimal.ZERO;

        String body = String.format(
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 12px; overflow: hidden;'>" +
                "  <div style='background: #1e293b; color: white; padding: 30px; text-align: center;'>" +
                "    <h2 style='margin: 0; text-transform: uppercase; letter-spacing: 2px;'>Installment Reminder</h2>" +
                "    <p style='margin: 10px 0 0; opacity: 0.9;'>Payment due for your enrollment</p>" +
                "  </div>" +
                "  <div style='padding: 40px; line-height: 1.6; color: #1e293b;'>" +
                "    <p>Hello <strong>%s</strong>,</p>" +
                "    <p>This is a reminder that your next installment of <strong>₹%s</strong> is due.</p>" +
                "    " +
                "    <div style='background: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                "       <table style='width: 100%%; font-size: 14px;'>" +
                "           <tr><td style='color: #64748b;'>Installment Amount:</td><td style='text-align: right; font-weight: bold;'>₹%s</td></tr>" +
                "           <tr><td style='color: #64748b;'>Due Date:</td><td style='text-align: right; font-weight: bold;'>%s</td></tr>" +
                "           <tr><td style='color: #64748b;'>Remaining Balance:</td><td style='text-align: right; font-weight: bold;'>₹%s</td></tr>" +
                "       </table>" +
                "    </div>" +
                "    " +
                "    <div style='margin: 30px 0; text-align: center;'>" +
                "      <a href='%s' style='background-color: #3b82f6; color: white; padding: 16px 32px; border-radius: 8px; text-decoration: none; font-weight: bold; display: inline-block;'>PAY INSTALLMENT SECURELY</a>" +
                "    </div>" +
                "    " +
                "    <p style='font-size: 14px; color: #64748b;'>Copy link: %s</p>" +
                "    " +
                "    <div style='border-top: 1px solid #f1f5f9; margin-top: 30px; padding-top: 20px; font-size: 12px; color: #94a3b8;'>" +
                "      Thank you for staying on track with your education journey." +
                "    </div>" +
                "  </div>" +
                "</div>",
                lead.getName(), payment.getAmount(), payment.getAmount(), 
                payment.getDueDate() != null ? payment.getDueDate().toLocalDate() : "N/A",
                balance, paymentUrl, paymentUrl);

        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            mailService.sendEmail(lead.getEmail(), subject, body);
        }
        
        payment.setReminderSent(true);
        paymentRepository.save(payment);
        
        // Update task status or create a new follow-up if needed
        createLeadTask(lead, LocalDateTime.now(), "Installment Reminder Sent - ₹" + payment.getAmount(), "EMI_COLLECTION");
    }

    @Transactional
    public void markAsPaid(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        lead.setStatus("CONVERTED");
        leadRepository.save(lead);

        log.info(">>> Lead {} manually marked as PAID/CONVERTED", lead.getEmail());
    }

    private void sendAdmissionSuccessEmail(Lead lead, Payment payment) {
        log.info(">>> SENDING PROFESSIONAL INVOICE to {}", lead.getEmail());
        String subject = "Admission Confirmed - Official Invoice #" + payment.getPaymentGatewayId();

        String body = String.format(
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden;'>" +
                "  <div style='background-color: #2e7d32; color: white; padding: 20px; text-align: center;'>" +
                "    <h2 style='margin: 0;'>ADMISSION CONFIRMED</h2>" +
                "    <p style='margin: 5px 0 0;'>Official Payment Receipt</p>" +
                "  </div>" +
                "  <div style='padding: 30px; line-height: 1.6; color: #333;'>" +
                "    <p>Dear <strong>%s</strong>,</p>" +
                "    <p>We are delighted to confirm that your payment has been successfully verified. Your admission is now official.</p>" +
                "    <div style='background-color: #f9f9f9; padding: 20px; border-radius: 4px; margin: 20px 0;'>" +
                "      <table style='width: 100%%; border-collapse: collapse;'>" +
                "        <tr><td style='padding: 8px 0; color: #666;'>Invoice ID:</td><td style='padding: 8px 0; text-align: right; font-weight: bold;'>%s</td></tr>" +
                "        <tr><td style='padding: 8px 0; color: #666;'>Amount Paid:</td><td style='padding: 8px 0; text-align: right; color: #2e7d32; font-weight: bold;'>₹%s</td></tr>" +
                "        <tr><td style='padding: 8px 0; color: #666;'>Method:</td><td style='padding: 8px 0; text-align: right;'>%s</td></tr>" +
                "        <tr><td style='padding: 8px 0; color: #666;'>Status:</td><td style='padding: 8px 0; text-align: right; color: #2e7d32; font-weight: bold;'>SUCCESSFUL</td></tr>" +
                "      </table>" +
                "    </div>" +
                "    <p>Your admission is confirmed. Our team will contact you shortly to discuss the next steps.</p>" +
                "    <p style='margin-top: 30px;'>Best Regards,<br/><strong>The Admissions Team</strong></p>" +
                "  </div>" +
                "  <div style='background-color: #f5f5f5; color: #888; padding: 15px; text-align: center; font-size: 12px; border-top: 1px solid #e0e0e0;'>" +
                "    This is a system-generated invoice for your transaction. No signature required." +
                "  </div>" +
                "</div>",
                lead.getName(), payment.getPaymentGatewayId(), payment.getAmount(),
                (payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "MANUAL"));
        
        // Async dispatch
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            try {
                mailService.sendEmail(lead.getEmail(), subject, body);
            } catch (Exception e) {
                log.error(">>> Email Delivery Failed for Invoice: {}", e.getMessage());
            }
        }
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
                .map(p -> convertToDTO(p))
                .orElseThrow(() -> new ResourceNotFoundException("No successful payment found for lead: " + leadId));
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

        boolean isGlobalAdmin = securityService.isAdmin(requester) && managerId == null && tlId == null && associateId == null && userId == null;

        Payment.Status pStatus = (status != null && !status.isEmpty()) ? Payment.Status.valueOf(status.toUpperCase()) : null;

        // Optimization: Use optimized lead fetching
        if (isGlobalAdmin) {
            return paymentRepository.findFiltered(null, start, end, pStatus).stream()
                    .map(p -> convertToDTO(p)).collect(Collectors.toList());
        } else {
            // Optimized query for hierarchy using direct JOIN
            return paymentRepository.findFilteredByUserHierarchy(new java.util.ArrayList<>(targetUserIds), start, end, pStatus).stream()
                    .map(p -> convertToDTO(p)).collect(Collectors.toList());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> getFilteredPaymentHistoryForTL(String username, LocalDateTime start, LocalDateTime end, String status, Long userId) {
        User tl = userRepository.findByEmail(username).orElseThrow();
        // TL looking at their team, we pass tl.getId() as tlId
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

        Payment payment = Payment.builder()
                .leadId(leadId)
                .amount(initialAmount)
                .totalAmount(totalAmount != null ? totalAmount : initialAmount)
                .status(Payment.Status.PENDING)
                .paymentType(splitRequest != null ? "EMI_INSTALLMENT" : "FULL")
                .build();

        BigDecimal minAmount = new BigDecimal("500");
        if (lead.getCourse() != null && lead.getCourse().getMinTokenAmount() != null) {
            minAmount = lead.getCourse().getMinTokenAmount();
        }

        if (initialAmount.compareTo(minAmount) < 0) {
            throw new InvalidRequestException("Minimum payment amount is ₹" + minAmount);
        }

        Payment saved = paymentRepository.save(payment);

        if (splitRequest != null) {
            splitPayment(saved.getId(), splitRequest);
        }

        syncStudentFee(lead, BigDecimal.ZERO, totalAmount, null, null);

        Map<String, String> response = new HashMap<>();
        response.put("payment_url", frontendUrl + "/payment-instruction/" + saved.getId());
        response.put("payment_session_id", "MANUAL_" + saved.getId());

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

        // Handle partial payment vs full payment
        BigDecimal paidThisTime = actualPaidAmount != null ? actualPaidAmount : payment.getAmount();
        
        if (actualPaidAmount != null && actualPaidAmount.compareTo(BigDecimal.ZERO) > 0 
                && actualPaidAmount.compareTo(payment.getAmount()) < 0) {
            handlePartialPayment(payment, lead, actualPaidAmount, nextDueDateStr);
        } else {
            handleFullPayment(payment, lead);
        }

        // AUTO-STATUS LOGIC: The initial token payment successfully secures the enrollment.
        // Convert the lead immediately. The StudentFee ledger will handle remaining EMI tracking.
        lead.setStatus("CONVERTED");
        leadRepository.save(lead);
    }

    private void handlePartialPayment(Payment payment, Lead lead, BigDecimal paidAmount, String nextDueDateStr) {
        BigDecimal remaining = payment.getAmount().subtract(paidAmount);
        payment.setAmount(paidAmount);
        payment.setPaymentType("INSTALLMENT");

        LocalDateTime nextDue = null;
        if (nextDueDateStr != null && !nextDueDateStr.isEmpty()) {
            nextDue = LocalDateTime.parse(nextDueDateStr.contains("T") ? nextDueDateStr : nextDueDateStr + "T10:00:00");
            
            Payment nextInstallment = Payment.builder()
                    .leadId(payment.getLeadId())
                    .amount(remaining)
                    .totalAmount(payment.getTotalAmount())
                    .status(Payment.Status.PENDING)
                    .paymentType("EMI_INSTALLMENT")
                    .dueDate(nextDue)
                    .build();
            paymentRepository.save(nextInstallment);

            lead.setFollowUpDate(nextDue);
            lead.setFollowUpRequired(true);
            lead.setFollowUpType("EMI_COLLECTION");
            leadRepository.save(lead);

            createLeadTask(lead, nextDue, "EMI Collection - Remainder", "EMI_COLLECTION");
        }
        syncStudentFee(lead, paidAmount, payment.getTotalAmount(), null, nextDue);
    }

    private void handleFullPayment(Payment payment, Lead lead) {
        // 1. Complete sales-related follow-ups
        List<LeadTask> followUps = leadTaskRepository.findByLeadId(lead.getId()).stream()
                .filter(t -> t.getStatus() == LeadTask.TaskStatus.PENDING)
                .filter(t -> !"EMI_COLLECTION".equalsIgnoreCase(t.getTaskType()))
                .collect(Collectors.toList());
        followUps.forEach(t -> t.setStatus(LeadTask.TaskStatus.COMPLETED));
        leadTaskRepository.saveAll(followUps);

        // 2. Smart-complete the specific EMI task if this is an installment payment
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

        sendAdmissionSuccessEmail(lead, payment);
        syncStudentFee(lead, payment.getAmount(), payment.getTotalAmount(), null, null);
    }

    @Transactional
    public PaymentDTO recordManualPayment(Map<String, Object> data) {
        Long leadId = Long.valueOf(data.get("leadId").toString());
        BigDecimal amount = new BigDecimal(data.get("amount").toString());
        BigDecimal totalAmount = data.containsKey("totalAmount") ? new BigDecimal(data.get("totalAmount").toString()) : amount;
        
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        User requester = securityService.getCurrentUser();
        
        if (lead.getAssignedTo() != null) {
            securityService.validateAccess(requester, lead.getAssignedTo().getId());
        }

        Payment payment = Payment.builder()
                .leadId(leadId)
                .amount(amount)
                .totalAmount(totalAmount)
                .status(Payment.Status.PAID)
                .paymentMethod((String) data.get("paymentMethod"))
                .note((String) data.get("note"))
                .paymentType((String) data.get("paymentType"))
                .paymentGatewayId("MANUAL_" + System.currentTimeMillis())
                .updatedBy(securityService.getCurrentUser())
                .build();

        Payment saved = paymentRepository.save(payment);
        processSuccessfulPayment(saved, amount, (String) data.get("nextDueDate"));

        return convertToDTO(saved);
    }

    private void syncStudentFee(Lead lead, BigDecimal paidAmount, BigDecimal totalAmount, BigDecimal discount, LocalDateTime nextDue) {
        StudentFee fee = studentFeeRepository.findByLeadId(lead.getId())
                .orElse(StudentFee.builder()
                        .leadId(lead.getId())
                        .studentName(lead.getName())
                        .studentEmail(lead.getEmail())
                        .studentMobile(lead.getMobile())
                        .totalAmount(totalAmount != null ? totalAmount : paidAmount)
                        .paidAmount(BigDecimal.ZERO)
                        .balanceAmount(totalAmount != null ? totalAmount : paidAmount)
                        .paidInstallments(0)
                        .build());

        // Update Total Amount if passed
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            fee.setTotalAmount(totalAmount);
        }

        // Update Discount if passed
        if (discount != null) {
            fee.setDiscount(discount);
        }

        BigDecimal currentPaid = fee.getPaidAmount() != null ? fee.getPaidAmount() : BigDecimal.ZERO;
        fee.setPaidAmount(currentPaid.add(paidAmount));
        
        // Calculate Installments
        List<Payment> allPayments = paymentRepository.findByLeadId(lead.getId());
        int total = allPayments.size();
        int paidCount = (int) allPayments.stream()
                .filter(p -> p.getStatus() == Payment.Status.PAID || p.getStatus() == Payment.Status.SUCCESS)
                .count();

        fee.setTotalInstallments(total);
        fee.setPaidInstallments(paidCount);

        if (fee.getTotalAmount() != null) {
            BigDecimal netTotal = fee.getTotalAmount().subtract(fee.getDiscount() != null ? fee.getDiscount() : BigDecimal.ZERO);
            fee.setBalanceAmount(netTotal.subtract(fee.getPaidAmount()));
        }
        if (nextDue != null) {
            fee.setNextDueDate(nextDue);
        }

        fee.setPaymentStatus(calculatePaymentStatus(fee));
        studentFeeRepository.save(fee);

        // All payment-related leads are CONVERTED
        if (!"CONVERTED".equalsIgnoreCase(lead.getStatus())) {
            lead.setStatus("CONVERTED");
            leadRepository.save(lead);
        }
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

    private void createLeadTask(Lead lead, LocalDateTime dueDate, String title, String type) {
        if (dueDate == null) return;
        LeadTask task = LeadTask.builder()
                .lead(lead)
                .title(title)
                .dueDate(dueDate)
                .status(LeadTask.TaskStatus.PENDING)
                .taskType(type)
                .assignedTo(lead.getAssignedTo())
                .build();
        leadTaskRepository.save(task);
        
        // Sync the lead's follow-up date to ensure it appears in "Today's Focus"
        if (lead.getFollowUpDate() == null || dueDate.isBefore(lead.getFollowUpDate()) || dueDate.toLocalDate().isEqual(java.time.LocalDate.now())) {
            lead.setFollowUpDate(dueDate);
            leadRepository.save(lead);
        }
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
            LocalDateTime dDate = part.getDueDate() != null ? LocalDateTime.parse(part.getDueDate().contains("T") ? part.getDueDate() : part.getDueDate() + "T10:00:00") : null;

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
                        .paymentType("EMI_INSTALLMENT")
                        .dueDate(dDate)
                        .build());
            }
            if (lead != null && dDate != null) {
                createLeadTask(lead, dDate, "Split EMI Part " + (i + 1), "EMI_COLLECTION");
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentFeeStructure(Long leadId) {
        StudentFee fee = studentFeeRepository.findByLeadId(leadId).orElse(null);
        List<PaymentDTO> payments = paymentRepository.findByLeadId(leadId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        if (fee != null) {
            Map<String, Object> feeMap = new HashMap<>();
            feeMap.put("totalAmount", fee.getTotalAmount());
            feeMap.put("discount", fee.getDiscount());
            feeMap.put("paidAmount", fee.getPaidAmount());
            feeMap.put("balanceAmount", fee.getBalanceAmount());
            feeMap.put("nextDueDate", fee.getNextDueDate());
            feeMap.put("paymentStatus", fee.getPaymentStatus());
            response.put("fee", feeMap);
        } else if (!payments.isEmpty()) {
            // Fallback: If installments exist but fee record is missing, 
            // calculate a virtual fee summary from the installments
            BigDecimal total = payments.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = payments.stream().filter(p -> "PAID".equalsIgnoreCase(p.getStatus())).map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Map<String, Object> feeMap = new HashMap<>();
            feeMap.put("totalAmount", total);
            feeMap.put("paidAmount", paid);
            feeMap.put("balanceAmount", total.subtract(paid));
            feeMap.put("nextDueDate", payments.stream().filter(p -> "PENDING".equalsIgnoreCase(p.getStatus())).map(p -> p.getDueDate()).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
            response.put("fee", feeMap);
        } else {
            response.put("fee", null);
        }
        response.put("installments", payments);
        return response;
    }

    private String calculatePaymentStatus(StudentFee fee) {
        if (fee == null) {
            return "DUE";
        }

        BigDecimal balance = fee.getBalanceAmount() != null
                ? fee.getBalanceAmount()
                : BigDecimal.ZERO;

        int paidInstallments = fee.getPaidInstallments() != null
                ? fee.getPaidInstallments()
                : 0;

        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }

        if (paidInstallments <= 1) {
            return "PRE_PAYMENT";
        }

        return "POST_PAYMENT_" + (paidInstallments - 1);
    }
}
