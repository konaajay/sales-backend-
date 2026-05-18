package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.PaymentDTO;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.entity.StudentFee;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import com.lms.www.leadmanagement.repository.StudentFeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentFeeService {

    private final StudentFeeRepository studentFeeRepository;
    private final PaymentRepository paymentRepository;
    private final LeadRepository leadRepository;

    private static final String STATUS_CONVERTED = "CONVERTED";

    @Transactional
    public void syncStudentFee(Lead lead, BigDecimal paidAmount, BigDecimal totalAmount, BigDecimal discount,
            LocalDateTime nextDue) {
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

        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            fee.setTotalAmount(totalAmount);
        }

        if (discount != null) {
            fee.setDiscount(discount);
        }

        List<Payment> allPayments = paymentRepository.findByLeadId(lead.getId());
        BigDecimal totalPaidCalculated = allPayments.stream()
                .filter(this::isSuccessfulPayment)
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        fee.setPaidAmount(totalPaidCalculated);

        int total = allPayments.size();
        int paidCount = (int) allPayments.stream()
                .filter(this::isSuccessfulPayment)
                .count();

        fee.setTotalInstallments(total);
        fee.setPaidInstallments(paidCount);

        if (fee.getTotalAmount() != null) {
            BigDecimal netTotal = fee.getTotalAmount()
                    .subtract(fee.getDiscount() != null ? fee.getDiscount() : BigDecimal.ZERO);
            fee.setBalanceAmount(netTotal.subtract(fee.getPaidAmount()));
        }
        if (nextDue != null) {
            fee.setNextDueDate(nextDue);
        }

        fee.setPaymentStatus(calculatePaymentStatus(fee));
        studentFeeRepository.save(fee);

        String newStatus = fee.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0 ? STATUS_CONVERTED : fee.getPaymentStatus();

        if (!newStatus.equalsIgnoreCase(lead.getStatus())) {
            log.info(">>> StudentFeeService: Transitioning Lead {} status from {} to {}", lead.getId(), lead.getStatus(), newStatus);
            lead.setStatus(newStatus);
            leadRepository.save(lead);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentFeeStructure(Long leadId) {
        StudentFee fee = studentFeeRepository.findByLeadId(leadId).orElse(null);
        List<Payment> rawPayments = paymentRepository.findByLeadId(leadId);
        
        List<PaymentDTO> payments = rawPayments.stream()
                .map(p -> {
                    Lead lead = leadRepository.findById(p.getLeadId()).orElse(null);
                    PaymentDTO dto = PaymentDTO.fromEntity(p, lead);
                    if (fee != null) {
                        dto.setTotalPackageAmount(fee.getTotalAmount());
                        dto.setPaidAmountSoFar(fee.getPaidAmount());
                        dto.setBalanceDue(fee.getBalanceAmount());
                        dto.setNextInstallmentDate(fee.getNextDueDate());
                    }
                    return dto;
                })
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
            BigDecimal total = payments.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = payments.stream()
                    .filter(p -> p.getStatus() != null && 
                            ("PAID".equalsIgnoreCase(p.getStatus()) || "SUCCESS".equalsIgnoreCase(p.getStatus()) || "COMPLETED".equalsIgnoreCase(p.getStatus())))
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> feeMap = new HashMap<>();
            feeMap.put("totalAmount", total);
            feeMap.put("paidAmount", paid);
            feeMap.put("balanceAmount", total.subtract(paid));
            feeMap.put("nextDueDate", payments.stream().filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                    .map(p -> p.getDueDate()).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
            response.put("fee", feeMap);
        } else {
            response.put("fee", null);
        }
        response.put("installments", payments);
        return response;
    }

    public String calculatePaymentStatus(StudentFee fee) {
        if (fee == null) {
            return "DUE";
        }

        if ("REJECTED".equalsIgnoreCase(fee.getPaymentStatus()) && (fee.getPaidInstallments() == null || fee.getPaidInstallments() == 0)) {
            return "REJECTED";
        }

        BigDecimal balance = fee.getBalanceAmount() != null ? fee.getBalanceAmount() : BigDecimal.ZERO;
        int paidInstallments = fee.getPaidInstallments() != null ? fee.getPaidInstallments() : 0;

        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return "FULL_PAID";
        }

        if (paidInstallments == 0) {
            return "PRE_PAYMENT";
        }

        return "PAID_INSTALLMENT_" + paidInstallments;
    }

    private boolean isSuccessfulPayment(Payment p) {
        if (p == null || p.getStatus() == null) return false;
        Payment.Status s = p.getStatus();
        return s == Payment.Status.PAID || s == Payment.Status.SUCCESS || s == Payment.Status.COMPLETED;
    }
}
