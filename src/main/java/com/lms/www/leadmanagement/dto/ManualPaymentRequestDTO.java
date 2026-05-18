package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualPaymentRequestDTO {

    private Long leadId;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String note;
    private String utr;
    private BigDecimal discount;
    private String nextDueDate;
    
    private String businessName;
    private String businessAddress;
    private String businessContact;
    private String businessEmail;
    private String taxId;
    private String paymentType;

    private List<InstallmentDetail> installments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstallmentDetail {
        private BigDecimal amount;
        private String dueDate;
        private String businessName;
        private String businessAddress;
        private String businessContact;
        private String businessEmail;
        private String taxId;
    }
}
