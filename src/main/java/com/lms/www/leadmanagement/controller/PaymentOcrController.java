package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.OcrResponseDTO;
import com.lms.www.leadmanagement.service.PaymentOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Adjust in production
public class PaymentOcrController {

    private final PaymentOcrService ocrService;

    @PostMapping("/api/payments/ocr")
    public ResponseEntity<OcrResponseDTO> extractData(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                OcrResponseDTO.builder()
                    .success(false)
                    .errorMessage("Image file is missing")
                    .build()
            );
        }

        OcrResponseDTO response = ocrService.extractPaymentData(file);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }
}
