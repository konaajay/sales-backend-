package com.lms.www.leadmanagement.dto;

import com.lms.www.leadmanagement.entity.Lead;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentDTO {
    private Long id;
    private Long leadId;
    private String leadName;
    private String leadEmail;
    private String courseName;
    private String mobile;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private java.time.LocalDateTime date;
    private String paymentMethod;
    private String paymentType;
    private java.time.LocalDateTime dueDate;
    private String status;
    private String paymentGatewayId;
    private String receiptUrl;
    private String note;
    private String assignedTlName;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private Long updatedById;

    // Student Fee Structure for Invoice
    private java.math.BigDecimal totalPackageAmount;
    private java.math.BigDecimal paidAmountSoFar;
    private java.math.BigDecimal balanceDue;
    private java.time.LocalDateTime nextInstallmentDate;
    
    // Business Details for Invoice
    private String businessName;
    private String businessAddress;
    private String businessContact;
    private String businessEmail;
    private String taxId;

    public static PaymentDTO fromEntity(com.lms.www.leadmanagement.entity.Payment payment, Lead lead) {
        PaymentDTOBuilder builder = PaymentDTO.builder()
                .id(payment.getId())
                .leadId(payment.getLeadId())
                .amount(payment.getAmount())
                .totalAmount(payment.getTotalAmount())
                .date(payment.getDate())
                .paymentMethod(payment.getPaymentMethod())
                .paymentType(payment.getPaymentType())
                .dueDate(payment.getDueDate())
                .status(payment.getStatus() != null ? payment.getStatus().name() : "PENDING")
                .paymentGatewayId(payment.getPaymentGatewayId())
                .receiptUrl(payment.getReceiptUrl())
                .note(payment.getNote())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .updatedById(payment.getUpdatedBy() != null ? payment.getUpdatedBy().getId() : null)
                
                // Business Details for Invoice
                .businessName(payment.getBusinessName() != null ? payment.getBusinessName() : "Gyantrix")
                .businessAddress(payment.getBusinessAddress() != null ? payment.getBusinessAddress() : "Pathrika Nagar, Street No:1, HITEC City, Hyderabad - 500081")
                .businessContact(payment.getBusinessContact() != null ? payment.getBusinessContact() : "+91 9247551330")
                .businessEmail(payment.getBusinessEmail() != null ? payment.getBusinessEmail() : "support@gyantrixacademy.com")
                .taxId(payment.getTaxId() != null ? payment.getTaxId() : "GSTIN: 36AAACG1234F1Z5");

        if (lead != null) {
            builder.leadName(lead.getName())
                   .leadEmail(lead.getEmail())
                   .mobile(lead.getMobile());
            if (lead.getCourse() != null) {
                builder.courseName(lead.getCourse().getName());
            }
            if (lead.getAssignedTo() != null) {
                builder.assignedTlName(lead.getAssignedTo().getName());
            }
        }

        return builder.build();
    }

}
