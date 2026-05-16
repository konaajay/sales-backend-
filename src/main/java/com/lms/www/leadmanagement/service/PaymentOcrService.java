package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.OcrResponseDTO;
import com.lms.www.leadmanagement.util.PaymentOcrParser;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class PaymentOcrService {

    private final Tesseract tesseract;

    public PaymentOcrService() {
        this.tesseract = new Tesseract();
        
        // IMPORTANT: In production, download tessdata from https://github.com/tesseract-ocr/tessdata
        // And set the path here. For now, we look in the project root/tessdata
        String tessDataPath = Paths.get("tessdata").toAbsolutePath().toString();
        
        // Ensure directory exists for safety
        File tessDir = new File(tessDataPath);
        File engData = new File(tessDir, "eng.traineddata");
        
        if (!tessDir.exists()) {
            tessDir.mkdirs();
        }
        
        if (engData.exists()) {
            log.info("OCR Protocol: 'eng.traineddata' detected at {}. Ready for extraction.", engData.getAbsolutePath());
        } else {
            log.error("CRITICAL: 'eng.traineddata' NOT FOUND at {}. OCR will fail until this file is present.", engData.getAbsolutePath());
        }
        
        this.tesseract.setDatapath(tessDataPath);
        this.tesseract.setLanguage("eng"); // Standard English OCR
        
        // Optimize for speed/accuracy
        this.tesseract.setVariable("user_defined_dpi", "300");
    }

    public OcrResponseDTO extractPaymentData(MultipartFile file) {
        try {
            // 1. Create temp file
            Path tempFile = Files.createTempFile("ocr-", file.getOriginalFilename());
            file.transferTo(tempFile.toFile());

            // 2. Perform OCR
            log.info("Starting OCR processing for file: {}", file.getOriginalFilename());
            String result = tesseract.doOCR(tempFile.toFile());
            log.info(">>> [OCR-RAW-RESULT]\n{}\n[OCR-RAW-END]", result);
            
            // 3. Parse result
            OcrResponseDTO dto = PaymentOcrParser.parse(result);
            
            // 4. Cleanup
            Files.deleteIfExists(tempFile);
            
            return dto;

        } catch (TesseractException e) {
            log.error("OCR Engine Failure: {}", e.getMessage());
            return OcrResponseDTO.builder()
                    .success(false)
                    .errorMessage("OCR Engine Failure: " + e.getMessage())
                    .build();
        } catch (IOException e) {
            log.error("File Processing Failure: {}", e.getMessage());
            return OcrResponseDTO.builder()
                    .success(false)
                    .errorMessage("File processing failed")
                    .build();
        }
    }
    
    /**
     * Optional: Pre-processing to improve accuracy for blurry screenshots
     */
    private BufferedImage preprocess(BufferedImage image) {
        // Image scaling, grayscale, or thresholding can be added here
        return image;
    }
}
