package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.service.CsvValidationService.ValidLeadMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.entity.StudentFee;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import com.lms.www.leadmanagement.repository.StudentFeeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LeadAssignmentService {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private StudentFeeRepository studentFeeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Transactional
    public List<Lead> assignAndBatchSave(List<ValidLeadMapping> validMappings, String batchId) {
        User currentUser = securityService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        List<Lead> leadsToSave = new ArrayList<>(validMappings.size());

        for (ValidLeadMapping mapping : validMappings) {
            String status = mapping.getRawRow().getStatus() != null && !mapping.getRawRow().getStatus().trim().isEmpty()
                    ? mapping.getRawRow().getStatus().trim()
                    : "OLD_LEAD";

            LocalDateTime leadCreatedAt = parseFlexibleDate(mapping.getRawRow().getCreatedDate());
            if (leadCreatedAt == null) {
                leadCreatedAt = now;
            }

            LocalDateTime followUpDate = parseFlexibleDate(mapping.getRawRow().getFollowUpDate());

            Lead lead = Lead.builder()
                    .name(mapping.getRawRow().getName().trim())
                    .email(mapping.getCleanEmail().isEmpty() ? null : mapping.getCleanEmail())
                    .mobile(mapping.getCleanMobile())
                    .course(mapping.getCourse())
                    .assignedTo(mapping.getAssignedUser())
                    .teamLeader(mapping.getTeamLeader())
                    .manager(mapping.getManager())
                    .note(mapping.getRawRow().getRemark())
                    .status(status)
                    .followUpDate(followUpDate)
                    .followUpRequired(followUpDate != null)
                    .createdAt(leadCreatedAt)
                    .createdBy(currentUser)
                    .importBatchId(batchId)
                    .importedBy(currentUser)
                    .importedAt(now)
                    .source("CSV_IMPORT")
                    .build();

            leadsToSave.add(lead);
        }

        // Use batch saveAll() for maximum performance on large CSVs
        List<Lead> savedLeads = leadRepository.saveAll(leadsToSave);
        log.info("Successfully batch saved {} leads for batchId {}", savedLeads.size(), batchId);

        // Process payments for imported leads
        List<StudentFee> feesToSave = new ArrayList<>();
        List<Payment> paymentsToSave = new ArrayList<>();

        for (int i = 0; i < savedLeads.size(); i++) {
            Lead savedLead = savedLeads.get(i);
            ValidLeadMapping mapping = validMappings.get(i);
            
            String totalFeeStr = mapping.getRawRow().getTotalFee();
            String paidAmtStr = mapping.getRawRow().getPaidAmount();
            
            if (totalFeeStr != null && !totalFeeStr.trim().isEmpty() && paidAmtStr != null && !paidAmtStr.trim().isEmpty()) {
                try {
                    BigDecimal totalFee = parseBigDecimal(totalFeeStr, BigDecimal.ZERO);
                    BigDecimal paidAmount = parseBigDecimal(paidAmtStr, BigDecimal.ZERO);
                    
                    String pendingStr = mapping.getRawRow().getPendingAmount();
                    BigDecimal pendingAmount = parseBigDecimal(pendingStr, null);
                    if (pendingAmount == null) {
                        pendingAmount = totalFee.subtract(paidAmount);
                    }
                    
                    // Clean up any stale/orphan fee records for this lead ID to prevent duplicate key violations
                    studentFeeRepository.findByLeadId(savedLead.getId()).ifPresent(existingFee -> {
                        log.info("Cleaning up stale student fee record for lead id: {}", savedLead.getId());
                        studentFeeRepository.delete(existingFee);
                    });
                    
                    // Clean up any stale/orphan payments for this lead ID
                    List<Payment> existingPayments = paymentRepository.findByLeadId(savedLead.getId());
                    if (existingPayments != null && !existingPayments.isEmpty()) {
                        log.info("Cleaning up {} stale payment records for lead id: {}", existingPayments.size(), savedLead.getId());
                        paymentRepository.deleteAll(existingPayments);
                    }
                    
                    StudentFee studentFee = StudentFee.builder()
                            .leadId(savedLead.getId())
                            .studentName(savedLead.getName())
                            .studentEmail(savedLead.getEmail())
                            .studentMobile(savedLead.getMobile())
                            .totalAmount(totalFee)
                            .paidAmount(paidAmount)
                            .balanceAmount(pendingAmount)
                            .paymentStatus(pendingAmount.compareTo(BigDecimal.ZERO) <= 0 ? "COMPLETED" : "PARTIAL")
                            .build();
                    feesToSave.add(studentFee);

                    if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                        LocalDateTime paymentDate = parseFlexibleDate(mapping.getRawRow().getPaymentDate());
                        if (paymentDate == null) {
                            paymentDate = now;
                        }
                        
                        String payMode = mapping.getRawRow().getPaymentMode();
                        String payType = mapping.getRawRow().getPaymentType();

                        Payment payment = Payment.builder()
                                .leadId(savedLead.getId())
                                .amount(paidAmount)
                                .totalAmount(totalFee)
                                .date(paymentDate)
                                .paymentMethod(payMode != null && !payMode.trim().isEmpty() ? payMode.trim() : "MANUAL")
                                .paymentType(payType != null && !payType.trim().isEmpty() ? payType.trim() : "FULL")
                                .status(Payment.Status.SUCCESS)
                                .note("Imported from CSV")
                                .updatedBy(currentUser)
                                .build();
                        paymentsToSave.add(payment);
                    }

                    if (pendingAmount.compareTo(BigDecimal.ZERO) > 0) {
                        LocalDateTime due = savedLead.getFollowUpDate() != null 
                                ? savedLead.getFollowUpDate() 
                                : LocalDateTime.now().plusWeeks(1);
                        
                        Payment pendingPayment = Payment.builder()
                                .leadId(savedLead.getId())
                                .amount(pendingAmount)
                                .totalAmount(totalFee)
                                .dueDate(due)
                                .paymentMethod("MANUAL")
                                .paymentType("EMI_INSTALLMENT")
                                .status(Payment.Status.PENDING)
                                .note("Pending installment imported from CSV")
                                .updatedBy(currentUser)
                                .build();
                        paymentsToSave.add(pendingPayment);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse payment info for lead {}, skipping payment creation: {}", savedLead.getId(), e.getMessage());
                }
            }
        }

        if (!feesToSave.isEmpty()) studentFeeRepository.saveAll(feesToSave);
        if (!paymentsToSave.isEmpty()) paymentRepository.saveAll(paymentsToSave);

        return savedLeads;
    }

    private LocalDateTime parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("N/A")) {
            return null;
        }
        try {
            String clean = dateStr.trim();
            // Split date from time if present (e.g. "17-05-2026 14:30")
            String timePart = "00:00:00";
            if (clean.contains(" ")) {
                String[] spaceParts = clean.split(" ");
                clean = spaceParts[0];
                if (spaceParts.length > 1) {
                    timePart = spaceParts[1];
                    if (timePart.split(":").length == 2) {
                        timePart += ":00";
                    }
                }
            } else if (clean.contains("T")) {
                String[] tParts = clean.split("T");
                clean = tParts[0];
                if (tParts.length > 1) {
                    timePart = tParts[1];
                }
            }
            
            // Normalize separators (e.g. slash or dot to dash)
            clean = clean.replace("/", "-").replace(".", "-");
            String[] parts = clean.split("-");
            if (parts.length == 3) {
                int year, month, day;
                if (parts[0].length() == 4) {
                    // YYYY-MM-DD
                    year = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]);
                    day = Integer.parseInt(parts[2]);
                } else {
                    // DD-MM-YYYY or D-M-YYYY
                    day = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]);
                    year = Integer.parseInt(parts[2]);
                }
                String isoStr = String.format("%04d-%02d-%02dT%s", year, month, day, timePart);
                return LocalDateTime.parse(isoStr);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date string: {}", dateStr, e);
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String str, BigDecimal defaultValue) {
        if (str == null || str.trim().isEmpty() || str.trim().equalsIgnoreCase("N/A")) {
            return defaultValue;
        }
        try {
            String clean = str.trim().replaceAll("[^\\d\\.]", "");
            return new BigDecimal(clean);
        } catch (Exception e) {
            log.warn("Failed to parse big decimal string: {}", str, e);
            return defaultValue;
        }
    }
}
