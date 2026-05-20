package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.CertificateRequest;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@Slf4j
public class PdfGeneratorService {

    // Brand colors for the GLogo template design
    private static final Color NAVY          = new Color(13, 27, 62);     // #0d1b3e
    private static final Color GOLD          = new Color(201, 168, 76);   // #c9a84c
    private static final Color GOLD_DARK     = new Color(184, 134, 11);   // #b8860b
    private static final Color WHITE         = new Color(255, 255, 255);
    private static final Color TEXT_BLACK    = new Color(17, 17, 17);     // #111
    private static final Color TEXT_DARK     = new Color(51, 51, 51);     // #333
    private static final Color TEXT_GRAY     = new Color(68, 68, 68);     // #444
    
    public byte[] generateCertificatePdf(CertificateRequest request, String certificateId, String issueDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // A4 Landscape: 842 x 595. Wait, template is 900 x 656. We'll map coordinates relatively.
            Rectangle pageSize = new Rectangle(PageSize.A4.getHeight(), PageSize.A4.getWidth());
            Document document = new Document(pageSize, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            PdfContentByte cb = writer.getDirectContent();
            float w = pageSize.getWidth();
            float h = pageSize.getHeight();

            // 1. Navy Outer Background (16px scaled)
            float navyPadding = 16f;
            cb.setColorFill(NAVY);
            cb.rectangle(0, 0, w, h);
            cb.fill();

            // 2. Gold Middle Background (6px scaled)
            float goldPadding = 6f;
            cb.setColorFill(GOLD_DARK);
            cb.rectangle(navyPadding, navyPadding, w - 2*navyPadding, h - 2*navyPadding);
            cb.fill();

            // 3. Inner White Background
            float totalPadding = navyPadding + goldPadding;
            cb.setColorFill(WHITE);
            cb.rectangle(totalPadding, totalPadding, w - 2*totalPadding, h - 2*totalPadding);
            cb.fill();

            // Fonts
            BaseFont bfTimesBold = BaseFont.createFont(BaseFont.TIMES_BOLD, BaseFont.CP1252, false);
            BaseFont bfTimesRoman = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, false);
            BaseFont bfTimesItalic = BaseFont.createFont(BaseFont.TIMES_ITALIC, BaseFont.CP1252, false);
            BaseFont bfHelvBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);
            BaseFont bfHelvReg = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);

            // Load Great Vibes font for signatures
            String fontPath = "src/main/resources/fonts/GreatVibes-Regular.ttf";
            // Check if file exists, if not try classpath (for jar)
            if (!new File(fontPath).exists()) {
                fontPath = new org.springframework.core.io.ClassPathResource("fonts/GreatVibes-Regular.ttf").getFile().getAbsolutePath();
            }
            BaseFont bfGreatVibes = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            float cx = w / 2f;
            float cy = h / 2f;

            // ── Images Loading Logic ───────────────────────────────────────────────
            // Tries to load from src/main/resources/static first for local dev, then classpath
            byte[] gy1Bytes = loadImage("gy1-png.png");
            byte[] isoBytes = loadImage("iso-new-badge.png");

            // ── Watermark (Center) ────────────────────────────────────────────────────
            cb.saveState();
            try {
                PdfGState gState = new PdfGState();
                gState.setFillOpacity(0.06f);
                cb.setGState(gState);
                if (gy1Bytes != null) {
                    Image watermarkImg = Image.getInstance(gy1Bytes);
                    watermarkImg.scaleToFit(420f, 420f);
                    // perfectly centered
                    watermarkImg.setAbsolutePosition((w - watermarkImg.getScaledWidth()) / 2f, (h - watermarkImg.getScaledHeight()) / 2f);
                    cb.addImage(watermarkImg);
                } else {
                    // Geometric Watermark Fallback if image not found
                    cb.setColorFill(NAVY);
                    cb.circle(cx, cy + 20f, 150f);
                    cb.fill();
                    cb.setColorFill(WHITE);
                    drawCenteredText(cb, bfHelvBold, 200f, "G", cx, cy - 40f);
                }
            } catch (Exception e) {
                log.warn("Watermark load failed", e);
            }
            cb.restoreState();

            // ── ISO Seal (top right) ─────────────────────────────────────────────────
            float badgeX = w - 120f;
            float badgeY = h - 120f; 
            
            try {
                if (isoBytes != null) {
                    Image isoImg = Image.getInstance(isoBytes);
                    isoImg.scaleToFit(190f, 190f); 
                    
                    float imgW = isoImg.getScaledWidth();
                    float imgX = w - 210f;
                    float imgY = h - 145f;

                    // Small dark blue rectangle tab exactly behind ISO, stretching to top
                    float hangerWidth = 30f;
                    float hangerX = imgX + (imgW / 2f) - (hangerWidth / 2f);
                    float hangerY = imgY + (isoImg.getScaledHeight() / 2f);
                    float hangerHeight = h - hangerY;
                    
                    cb.setColorFill(NAVY);
                    cb.rectangle(hangerX, hangerY, hangerWidth, hangerHeight);
                    cb.fill();

                    isoImg.setAbsolutePosition(imgX, imgY);
                    cb.addImage(isoImg);
                } else {
                    // Geometric Fallback
                    float isoCenterY = badgeY;
                    cb.setColorFill(GOLD);
                    cb.circle(badgeX, isoCenterY, 35f);
                    cb.fill();
                    cb.setColorFill(WHITE);
                    drawCenteredText(cb, bfHelvBold, 15f, "ISO 9001", badgeX, isoCenterY - 4f);
                }
            } catch (Exception e) {
                 log.warn("ISO Seal load failed", e);
            }

            // ── Header Text ──────────────────────────────────────────────────────────
            float yPos = h - 105;
            cb.setColorFill(NAVY);
            cb.beginText();
            cb.setFontAndSize(bfTimesBold, 48f);
            try { cb.setCharacterSpacing(8f); } catch (Exception ignored) {} // Letter spacing 8px
            String title = "CERTIFICATE";
            float titleW = bfTimesBold.getWidthPoint(title, 48f) + (title.length() - 1) * 8f;
            cb.setTextMatrix((w - titleW) / 2f, yPos);
            cb.showText(title);
            cb.endText();

            yPos -= 32;
            cb.beginText();
            cb.setFontAndSize(bfHelvBold, 17f);
            try { cb.setCharacterSpacing(2f); } catch (Exception ignored) {}
            String pTitle = "OF PARTICIPATION";
            float ptW = bfHelvBold.getWidthPoint(pTitle, 17f) + (pTitle.length() - 1) * 2f;
            cb.setTextMatrix((w - ptW) / 2f, yPos);
            cb.showText(pTitle);
            try { cb.setCharacterSpacing(0f); } catch (Exception ignored) {}
            cb.endText();

            // ── Visual Gold Row 1 ───────────────────────────────────────────────────────
            yPos -= 28;
            cb.setColorFill(GOLD);
            cb.rectangle(cx - 80f, yPos, 70f, 1f);
            cb.fill();
            cb.saveState();
            // Quick diamond rotation approximation via polygon
            float[] diamondX = {cx, cx - 4, cx, cx + 4};
            float[] diamondY = {yPos + 4, yPos, yPos - 4, yPos};
            cb.setColorFill(GOLD);
            cb.moveTo(diamondX[0], diamondY[0]);
            for (int i=1; i<4; i++) cb.lineTo(diamondX[i], diamondY[i]);
            cb.closePath();
            cb.fill();
            cb.restoreState();
            cb.rectangle(cx + 10f, yPos, 70f, 1f);
            cb.fill();

            // ── "This is to certify that" ──────────────────────────────────────────────
            yPos -= 32;
            cb.setColorFill(TEXT_GRAY);
            drawCenteredText(cb, bfTimesItalic, 14f, "This is to certify that", cx, yPos);

            // ── Student Name ───────────────────────────────────────────────────────────
            yPos -= 48;
            cb.setColorFill(TEXT_BLACK);
            drawCenteredText(cb, bfTimesBold, 54f, request.getStudentName(), cx, yPos);

            // ── Visual Gold Row 2 ──────────────────────────────────────────────────────
            yPos -= 35;
            cb.setColorFill(GOLD);
            cb.rectangle(cx - 150f, yPos, 140f, 1f);
            cb.rectangle(cx + 10f, yPos, 140f, 1f);
            cb.fill();
            cb.saveState();
            float[] diamond2Y = {yPos + 4, yPos, yPos - 4, yPos};
            cb.moveTo(diamondX[0], diamond2Y[0]);
            for (int i=1; i<4; i++) cb.lineTo(diamondX[i], diamond2Y[i]);
            cb.closePath();
            cb.fill();
            cb.restoreState();

            // ── Participation Text ─────────────────────────────────────────────────────
            yPos -= 45;
            cb.setColorFill(TEXT_DARK);
            String courseName = request.getWebinarName() != null ? request.getWebinarName() : "Professional Certification Program";
            String bodyLine1 = "has actively participated in the " + courseName + " verified by ISO 9001 \u2013 2015.";
            String bodyLine2 = "We appreciate the active involvement, enthusiasm, and valuable contribution to the conference.";
            
            drawCenteredText(cb, bfHelvReg, 13.5f, bodyLine1, cx, yPos);
            yPos -= 22;
            drawCenteredText(cb, bfHelvReg, 13.5f, bodyLine2, cx, yPos);

            // ── Signatures Row ─────────────────────────────────────────────────────────
            float sigY = 120f;
            float leftCenterX = totalPadding + 120f;
            float rightCenterX = w - totalPadding - 120f;

            // Left (N. surya)
            cb.setColorFill(NAVY);
            drawCenteredText(cb, bfGreatVibes, 30f, "N. surya", leftCenterX, sigY + 25);

            // line
            cb.setColorStroke(TEXT_BLACK);
            cb.setLineWidth(1.5f);
            cb.moveTo(leftCenterX - 75f, sigY + 8);
            cb.lineTo(leftCenterX + 75f, sigY + 8);
            cb.stroke();

            // designation
            cb.setColorFill(NAVY);
            drawCenteredText(cb, bfHelvBold, 12f, "N. SURYA", leftCenterX, sigY - 10);
            cb.setColorFill(TEXT_GRAY);
            drawCenteredText(cb, bfHelvReg, 11f, "Operational Manager", leftCenterX, sigY - 24);


            // Right (Varalakshmi)
            cb.setColorFill(NAVY);
            drawCenteredText(cb, bfGreatVibes, 30f, "Varalakshmi", rightCenterX, sigY + 25);

            // line
            cb.setColorStroke(TEXT_BLACK);
            cb.setLineWidth(1.5f);
            cb.moveTo(rightCenterX - 75f, sigY + 8);
            cb.lineTo(rightCenterX + 75f, sigY + 8);
            cb.stroke();

            // designation
            cb.setColorFill(NAVY);
            drawCenteredText(cb, bfHelvBold, 12f, "M. VARALAKSHMI", rightCenterX, sigY - 10);
            cb.setColorFill(TEXT_GRAY);
            drawCenteredText(cb, bfHelvReg, 11f, "Chief Operating Officer", rightCenterX, sigY - 24);

            // Center Logo block
            if (gy1Bytes != null) {
                Image centerLogo = Image.getInstance(gy1Bytes);
                centerLogo.scaleToFit(90f, 90f);
                centerLogo.setAbsolutePosition(cx - 45f, sigY);
                cb.addImage(centerLogo);
            }
            cb.setColorFill(new Color(58, 143, 212)); // #3a8fd4
            drawCenteredText(cb, bfHelvBold, 18f, "Gyantrix", cx, sigY - 10f);
            cb.setColorFill(new Color(112, 96, 192)); // #7060c0
            drawCenteredText(cb, bfHelvReg, 14f, "Academy", cx, sigY - 27f);

            // ── Footer Date & ID ───────────────────────────────────────────────────────
            float footerY = totalPadding + 28f;
            cb.setColorFill(TEXT_BLACK);
            cb.setFontAndSize(bfHelvBold, 12.5f);

            // Cert ID Left
            cb.beginText();
            cb.setTextMatrix(totalPadding + 40f, footerY);
            cb.showText("Cert ID: ");
            cb.setFontAndSize(bfHelvReg, 12.5f);
            cb.showText(certificateId != null ? certificateId : "");
            cb.endText();

            // Date Right
            String dateLabel = "Date: ";
            float dL = bfHelvBold.getWidthPoint(dateLabel, 12.5f);
            float dV = bfHelvReg.getWidthPoint(issueDate != null ? issueDate : "", 12.5f);
            cb.beginText();
            cb.setFontAndSize(bfHelvBold, 12.5f);
            cb.setTextMatrix(w - totalPadding - 40f - dL - dV, footerY);
            cb.showText(dateLabel);
            cb.setFontAndSize(bfHelvReg, 12.5f);
            cb.showText(issueDate != null ? issueDate : "");
            cb.endText();

            document.close();
            log.info("GLogo Dark Blue PDF generated for: {}", request.getStudentName());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for {}: {}", request.getStudentName(), e.getMessage());
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private byte[] loadImage(String fileName) {
        try {
            // Priority 1: Read locally directly from src folders (Development environment)
            File devFile = new File("src/main/resources/static/" + fileName);
            if (devFile.exists()) {
                return Files.readAllBytes(Paths.get(devFile.toURI()));
            }
            
            // Priority 2: Read from compiled classpath
            return new org.springframework.core.io.ClassPathResource("static/" + fileName).getInputStream().readAllBytes();
        } catch (Exception e) {
            log.warn("Could not find image: {}. Ensure it's located in src/main/resources/static/", fileName);
            return null; // Triggers graphical fallback
        }
    }

    private void drawCenteredText(PdfContentByte cb, BaseFont bf, float size, String text, float x, float y) {
        if (text == null) return;
        float textWidth = bf.getWidthPoint(text, size);
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setTextMatrix(x - (textWidth / 2f), y);
        cb.showText(text);
        cb.endText();
    }
}
