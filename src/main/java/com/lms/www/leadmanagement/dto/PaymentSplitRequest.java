package com.lms.www.leadmanagement.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaymentSplitRequest {
    private List<InstallmentPart> installments;
    private String paymentMethod;
    private String note;

    @Data
    public static class InstallmentPart {
        private BigDecimal amount;
        private String dueDate; // LocalDateTime string or date string
        private boolean isPaid; // Whether this part is paid now
    }
}
