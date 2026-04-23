package com.lms.www.leadmanagement.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username}")
    private String fromEmail;

    @SuppressWarnings("all")
    public void sendEmail(String to, String subject, String body) {
        System.out.println(">>> ATTEMPTING TO SEND EMAIL VIA: " + fromEmail + " TO: " + to);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true for HTML
            
            System.out.println(">>> CONNECTING TO SMTP SERVER FOR: " + to);
            mailSender.send(message);
            System.out.println(">>> SUCCESSFULLY SENT EMAIL TO: " + to);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            System.err.println(">>> ERROR: FAILED TO SEND EMAIL TO: " + to);
            System.err.println(">>> REASON: " + e.getMessage());
            log.error("Failed to send email to {}", to, e);
        }
    }

    public void sendPaymentLink(String to, String paymentUrl) {
        String subject = "Action Required: Your Admission Payment Link for LMS";
        String body = String.format(
            "<h3>Hello!</h3>" +
            "<p>We're excited to have you join our program.</p>" +
            "<p>To complete your admission, please use the secure payment link below:</p>" +
            "<p><a href='%s' style='background:#007bff;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;'>Pay Now & Complete Admission</a></p>" +
            "<p>Alternatively, copy and paste this link: <br/> %s</p>" +
            "<br/><p>Best regards,<br/>LMS Team</p>",
            paymentUrl, paymentUrl
        );
        sendEmail(to, subject, body);
    }

    public void sendUserCredentials(String to, String password, String name) {
        String subject = "Welcome to NEXUS CRM: Your Administrative Credentials";
        String body = String.format(
            "<h3>Welcome to the Team, %s!</h3>" +
            "<p>Your official administrative node has been established on NEXUS CRM.</p>" +
            "<div style='background:#f8fafc;padding:20px;border-left:4px solid #6366f1;border-radius:8px;'>" +
            "   <p style='margin-bottom:10px;'><strong>Login Credentials:</strong></p>" +
            "   <p style='margin:5px 0;'><strong>Username/Email:</strong> %s</p>" +
            "   <p style='margin:5px 0;'><strong>Secure Password:</strong> %s</p>" +
            "</div>" +
            "<p style='margin-top:20px;'><a href='http://localhost:3000/login' style='background:#6366f1;color:white;padding:12px 24px;text-decoration:none;border-radius:30px;font-weight:bold;'>Access Dashboard</a></p>" +
            "<p>Please ensure you change your password upon initial authentication for security purposes.</p>" +
            "<br/><p>Best regards,<br/>NEXUS Intelligence Cluster</p>",
            name, to, password
        );
        sendEmail(to, subject, body);
    }
}
