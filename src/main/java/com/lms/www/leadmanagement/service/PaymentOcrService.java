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
        
        if (!engData.exists()) {
            log.error("CRITICAL: 'eng.traineddata' NOT FOUND at {}. OCR will fail until this file is present.", engData.getAbsolutePath());
        }
        
        this.tesseract.setDatapath(tessDataPath);
        this.tesseract.setLanguage("eng"); // Standard English OCR
        
        // Optimize for speed/accuracy
        this.tesseract.setVariable("user_defined_dpi", "300");
    }

    public OcrResponseDTO extractPaymentData(MultipartFile file) {
        File tempFile = null;
        try {
            BufferedImage originalImage = null;
            try {
                originalImage = ImageIO.read(file.getInputStream());
            } catch (Exception e) {
                log.warn("ImageIO failed to read image input stream: {}", e.getMessage());
            }

            String result;
            if (originalImage != null) {
                BufferedImage preprocessedImage = preprocess(originalImage);
                tempFile = File.createTempFile("ocr-preprocessed-", ".png");
                ImageIO.write(preprocessedImage, "png", tempFile);
                result = tesseract.doOCR(tempFile);
            } else {
                log.warn("Falling back to original file OCR due to image reading issue...");
                tempFile = File.createTempFile("ocr-fallback-", file.getOriginalFilename());
                file.transferTo(tempFile);
                result = tesseract.doOCR(tempFile);
            }

            // Parse result
            OcrResponseDTO dto = PaymentOcrParser.parse(result);
            return dto;

        } catch (Exception e) {
            log.error("OCR Engine Failure (Tesseract could not process the file): {}", e.getMessage(), e);
            return OcrResponseDTO.builder()
                    .success(false)
                    .errorMessage("OCR extraction failed. Please enter UTR and amount details manually.")
                    .build();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete();
                } catch (Exception e) {
                    log.warn("Failed to delete temp OCR file: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Pre-processing to improve accuracy for blurry screenshots
     */
    private BufferedImage preprocess(BufferedImage image) {
        try {
            if (image == null) return null;
            
            // 1. Scale image (upscale by 2x for better OCR recognition if width is relatively small)
            int targetWidth = image.getWidth() * 2;
            int targetHeight = image.getHeight() * 2;
            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();
            
            // 2. Grayscale conversion
            BufferedImage gray = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
            java.awt.Graphics g = gray.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            
            // 3. Sharpening filter to enhance text contrast and edges
            float[] sharpenKernel = {
                0f, -1f, 0f,
                -1f, 5f, -1f,
                0f, -1f, 0f
            };
            java.awt.image.Kernel kernel = new java.awt.image.Kernel(3, 3, sharpenKernel);
            java.awt.image.ConvolveOp convolve = new java.awt.image.ConvolveOp(kernel, java.awt.image.ConvolveOp.EDGE_NO_OP, null);
            BufferedImage sharpened = convolve.filter(gray, null);
            
            return sharpened;
        } catch (Exception e) {
            log.warn("Image preprocessing failed, falling back to original: {}", e.getMessage());
            return image;
        }
    }
}
