package com.lms.www.leadmanagement.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class LeadPaymentRequestDTO {
    private BigDecimal totalAmount;
    private BigDecimal initialAmount;
    private String paymentType; // FULL or PART
    private String note;
    private List<PaymentSplitRequest.InstallmentPart> installments;

    public PaymentSplitRequest toSplitRequest() {
        PaymentSplitRequest split = new PaymentSplitRequest();
        split.setInstallments(this.installments);
        split.setNote(this.note);
        return split;
    }
}
