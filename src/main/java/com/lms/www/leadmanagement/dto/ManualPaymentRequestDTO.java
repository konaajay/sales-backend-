package com.lms.www.leadmanagement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManualPaymentRequestDTO {

    private Long leadId;
    private BigDecimal amount;
    private BigDecimal totalAmount;

    @JsonAlias({"method", "paymentMethod"})
    private String paymentMethod;

    private String method;
    private String paymentDate;
    private Long installmentId;
    private Long courseId;

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

    public String getPaymentMethod() {
        if (paymentMethod != null) return paymentMethod;
        return method;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
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
