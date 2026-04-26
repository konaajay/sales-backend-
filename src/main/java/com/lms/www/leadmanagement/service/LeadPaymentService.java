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

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public void markAsPaid(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        lead.setStatus(LeadStatus.CONVERTED.name());
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
        mailService.sendEmail(lead.getEmail(), subject, body);
    }

    public List<UserDTO> getTeamLeaders() {
        return userRepository.findByRoleName("TEAM_LEADER").stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public PaymentDTO generateInvoice(Long leadId) {
        return paymentRepository.findByLeadIdAndStatus(leadId, Payment.Status.PAID).stream()
                .max(Comparator.comparing(Payment::getCreatedAt))
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("No successful payment found for lead: " + leadId));
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> getFilteredPaymentHistory(Long userId, Long tlId, Long associateId,
            LocalDateTime start, LocalDateTime end, String status) {
        User requester = securityService.getCurrentUser();
        
        List<Long> allowedLeadIds;
        List<Long> targetUserIds = new ArrayList<>();

        if (userId != null) {
            securityService.validateHierarchyAccess(requester, userRepository.findById(userId).orElseThrow());
            targetUserIds.add(userId);
        } else if (associateId != null) {
            securityService.validateHierarchyAccess(requester, userRepository.findById(associateId).orElseThrow());
            targetUserIds.add(associateId);
        } else if (tlId != null) {
            User tl = userRepository.findById(tlId).orElseThrow();
            securityService.validateHierarchyAccess(requester, tl);
            targetUserIds.addAll(userRepository.findSubordinateIds(tlId));
            targetUserIds.add(tlId);
        } else {
            // Requester's full hierarchy
            if (!securityService.isAdmin(requester)) {
                targetUserIds.addAll(userRepository.findSubordinateIds(requester.getId()));
                targetUserIds.add(requester.getId());
            }
        }

        Payment.Status pStatus = (status != null && !status.isEmpty()) ? Payment.Status.valueOf(status.toUpperCase()) : null;

        // Optimization: Use optimized lead fetching
        if (targetUserIds.isEmpty()) {
            // Admin Global View
            return paymentRepository.findFiltered(null, start, end, pStatus).stream()
                    .map(this::convertToDTO).collect(Collectors.toList());
        } else {
            // Optimized query for hierarchy
            List<Long> leadIds = leadRepository.findByAssignedToInOrCreatedByIn(
                    userRepository.findAllById(targetUserIds), 
                    userRepository.findAllById(targetUserIds)
            ).stream().map(Lead::getId).collect(Collectors.toList());

            if (leadIds.isEmpty()) return Collections.emptyList();

            return paymentRepository.findFiltered(leadIds, start, end, pStatus).stream()
                    .map(this::convertToDTO).collect(Collectors.toList());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> getFilteredPaymentHistoryForTL(String username, LocalDateTime start, LocalDateTime end, String status, Long userId) {
        User tl = userRepository.findByEmail(username).orElseThrow();
        return getFilteredPaymentHistory(userId, tl.getId(), null, start, end, status);
    }

    @Transactional
    public Map<String, String> createPaymentLink(Long leadId, BigDecimal initialAmount, BigDecimal totalAmount,
            PaymentSplitRequest splitRequest) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

        Payment payment = Payment.builder()
                .leadId(leadId)
                .amount(initialAmount)
                .totalAmount(totalAmount != null ? totalAmount : initialAmount)
                .status(Payment.Status.PENDING)
                .paymentType(splitRequest != null ? "EMI_INSTALLMENT" : "FULL")
                .build();

        Payment saved = paymentRepository.save(payment);

        if (splitRequest != null) {
            splitPayment(saved.getId(), splitRequest);
        }

        syncStudentFee(lead, BigDecimal.ZERO, totalAmount, null);

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

        lead.setStatus(LeadStatus.CONVERTED.name());
        lead.setFollowUpRequired(false);
        leadRepository.save(lead);

        // Handle partial payment vs full payment
        if (actualPaidAmount != null && actualPaidAmount.compareTo(BigDecimal.ZERO) > 0 
                && actualPaidAmount.compareTo(payment.getAmount()) < 0) {
            handlePartialPayment(payment, lead, actualPaidAmount, nextDueDateStr);
        } else {
            handleFullPayment(payment, lead);
        }
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
        syncStudentFee(lead, paidAmount, payment.getTotalAmount(), nextDue);
    }

    private void handleFullPayment(Payment payment, Lead lead) {
        // Complete pending tasks
        List<LeadTask> tasks = leadTaskRepository.findByLeadId(lead.getId()).stream()
                .filter(t -> t.getStatus() == LeadTask.TaskStatus.PENDING)
                .collect(Collectors.toList());
        tasks.forEach(t -> t.setStatus(LeadTask.TaskStatus.COMPLETED));
        leadTaskRepository.saveAll(tasks);

        sendAdmissionSuccessEmail(lead, payment);
        syncStudentFee(lead, payment.getAmount(), payment.getTotalAmount(), null);
    }

    @Transactional
    public PaymentDTO recordManualPayment(Map<String, Object> data) {
        Long leadId = Long.valueOf(data.get("leadId").toString());
        BigDecimal amount = new BigDecimal(data.get("amount").toString());
        BigDecimal totalAmount = data.containsKey("totalAmount") ? new BigDecimal(data.get("totalAmount").toString()) : amount;
        
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new ResourceNotFoundException("Lead not found"));

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

    private void syncStudentFee(Lead lead, BigDecimal paidAmount, BigDecimal totalAmount, LocalDateTime nextDue) {
        StudentFee fee = studentFeeRepository.findByLeadId(lead.getId())
                .orElse(StudentFee.builder()
                        .leadId(lead.getId())
                        .studentName(lead.getName())
                        .studentEmail(lead.getEmail())
                        .studentMobile(lead.getMobile())
                        .totalAmount(totalAmount != null ? totalAmount : paidAmount)
                        .paidAmount(BigDecimal.ZERO)
                        .balanceAmount(totalAmount != null ? totalAmount : paidAmount)
                        .build());

        BigDecimal currentPaid = fee.getPaidAmount() != null ? fee.getPaidAmount() : BigDecimal.ZERO;
        fee.setPaidAmount(currentPaid.add(paidAmount));
        
        if (fee.getTotalAmount() != null) {
            fee.setBalanceAmount(fee.getTotalAmount().subtract(fee.getPaidAmount()));
        }
        if (nextDue != null) {
            fee.setNextDueDate(nextDue);
        }
        studentFeeRepository.save(fee);
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
                .build();
        leadTaskRepository.save(task);
    }

    @Transactional
    public void splitPayment(Long paymentId, PaymentSplitRequest splitRequest) {
        Payment original = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (original.getStatus() == Payment.Status.PAID) {
            throw new InvalidRequestException("Cannot split a completed payment");
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
        List<Payment> payments = paymentRepository.findByLeadId(leadId);
        
        Map<String, Object> response = new HashMap<>();
        if (fee != null) {
            Map<String, Object> feeMap = new HashMap<>();
            feeMap.put("totalAmount", fee.getTotalAmount());
            feeMap.put("paidAmount", fee.getPaidAmount());
            feeMap.put("balanceAmount", fee.getBalanceAmount());
            feeMap.put("nextDueDate", fee.getNextDueDate());
            feeMap.put("paymentStatus", fee.getPaymentStatus());
            response.put("fee", feeMap);
        } else {
            response.put("fee", null);
        }
        response.put("payments", payments);
        return response;
    }
}
