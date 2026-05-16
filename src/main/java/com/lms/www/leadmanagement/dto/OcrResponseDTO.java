package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResponseDTO {
    private boolean success;
    private String payerName;
    private String utrNumber;
    private String amount;
    private String paymentDate;
    private String paymentTime;
    private String paymentApp;
    private String rawText;
    private String errorMessage;
}
