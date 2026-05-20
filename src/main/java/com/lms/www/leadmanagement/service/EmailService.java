package com.lms.www.leadmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.lms.www.leadmanagement.dto.CertificateRequest;
import com.lms.www.leadmanagement.entity.Certificate;

import jakarta.mail.internet.MimeMessage;


@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final PdfGeneratorService pdfGeneratorService;
    private final FileStorageService fileStorageService;

    public void sendCertificateEmail(Certificate certificate, int retryCount) {
        try {
            byte[] pdfBytes = pdfGeneratorService.generateCertificatePdf(
                new CertificateRequest()
                    .setStudentName(certificate.getStudentName())
                    .setWebinarName(certificate.getWebinarName())
                    .setEmail(certificate.getEmail()),
                certificate.getCertificateId(),
                certificate.getIssueDate()
            );

            // Save to backend storage
            String storagePath = fileStorageService.saveCertificate(certificate.getCertificateId(), pdfBytes);
            certificate.setStoragePath(storagePath);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String subject = "Congratulations on Your Webinar Participation";
            String htmlBody = buildHtmlEmail(
                certificate.getStudentName(),
                certificate.getWebinarName(),
                certificate.getCertificateId(),
                certificate.getIssueDate()
            );

            helper.setTo(certificate.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addAttachment("certificate_" + certificate.getCertificateId() + ".pdf",
                               new jakarta.mail.util.ByteArrayDataSource(pdfBytes, "application/pdf"));

            mailSender.send(mimeMessage);
            log.info("HTML email sent successfully to {} (attempt {}/{})",
                    certificate.getEmail(), retryCount + 1, certificate.getRetryCount() + 1);

        } catch (Exception e) {
            log.error("Failed to send email to {} (attempt {}): {}",
                     certificate.getEmail(), retryCount + 1, e.getMessage());
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    private String buildHtmlEmail(String studentName, String webinarName, String certId, String issueDate) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>Participation Certificate</title>
            </head>
            <body style="font-family: Arial, sans-serif; color: #333; line-height: 1.6; max-width: 600px; margin: 0 auto; padding: 20px;">
              <p>Dear Aspirant,</p>
              
              <p>Greetings from Gyantrix Academy,</p>
              
              <p>Thank you for participating in the webinar on <strong>&ldquo;%s&rdquo;</strong>.</p>
              
              <p>We appreciate your time and interest.<br/>
              Please find attached your Certificate of Participation for the session.</p>
              
              <p>We hope the webinar provided valuable insights and added to your learning. We look forward to your participation in our upcoming events.</p>
              
              <p>For any queries, feel free to contact us.</p>
              
              <p style="margin-top: 30px;">
                Happy Learning<br/>
                Regards,<br/>
                <strong>Gyantrix</strong>
              </p>
              
              <hr style="border: 0; border-top: 1px solid #eee; margin-top: 40px; margin-bottom: 20px;" />
              <p style="font-size: 11px; color: #999;">
                Certificate ID: %s | Issue Date: %s<br/>
                &copy; 2025 Gyantrix Academy | All Rights Reserved
              </p>
            </body>
            </html>
            """, webinarName, certId, issueDate);
    }
}
