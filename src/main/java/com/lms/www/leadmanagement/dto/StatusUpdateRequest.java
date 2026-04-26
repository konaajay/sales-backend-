package com.lms.www.leadmanagement.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class StatusUpdateRequest {
    private String status;
    private String note;
    private String paymentType;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private Integer installmentCount;
    private String nextInstallmentDate;
    private String paymentMethod;
    private String dueDate;
    private List<InstallmentMap> installments;

    @Data
    public static class InstallmentMap {
        private BigDecimal amount;
        private String dueDate;
    }
}
