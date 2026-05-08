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
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private java.time.LocalDateTime date;
    private String paymentMethod;
    private String paymentType;
    private java.time.LocalDateTime dueDate;
    private String status;
    private String paymentGatewayId;
    private String receiptUrl;
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
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .updatedById(payment.getUpdatedBy() != null ? payment.getUpdatedBy().getId() : null)
                
                // Hardcoded Business Details for Invoice
                .businessName("Nexus Lead Management")
                .businessAddress("Techno Park, Hub 7, Bangalore, India")
                .businessContact("+91 98765 43210")
                .businessEmail("finance@nexuslms.com")
                .taxId("GSTIN: 29AABCB1234F1Z5");

        if (lead != null) {
            builder.leadName(lead.getName())
                   .leadEmail(lead.getEmail());
            if (lead.getAssignedTo() != null) {
                builder.assignedTlName(lead.getAssignedTo().getName());
            }
        }

        return builder.build();
    }

}
