package com.lms.www.leadmanagement.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url:http://52.87.168.111}")
    private String frontendUrl;

    public void sendEmail(String to, String subject, String body) {
        log.info("Attempting to send email to: {}", to);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            mailSender.send(message);
            log.info("Successfully sent email to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendPaymentLink(String to, String paymentUrl) {
        Context context = new Context();
        context.setVariable("paymentUrl", paymentUrl);
        String body = templateEngine.process("emails/payment-link", context);
        sendEmail(to, "Action Required: Your Admission Payment Link | Gyantric CRM", body);
    }

    @Async
    public void sendUserCredentials(String to, String password, String name) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("email", to);
        context.setVariable("password", password);
        context.setVariable("loginUrl", frontendUrl + "/login");

        String body = templateEngine.process("emails/welcome-email", context);
        sendEmail(to, "Welcome to Gyantric CRM | Your Account is Ready", body);
    }

    @Async
    public void sendOtp(String to, String otp, String name) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("otp", otp);

        String body = templateEngine.process("emails/otp-email", context);
        sendEmail(to, "Your Verification Code | Gyantric CRM", body);
    }

    @Async
    public void sendOverdueReminder(String to, String name, java.math.BigDecimal amount, String dueDate) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("amount", amount);
        context.setVariable("dueDate", dueDate);

        String body = templateEngine.process("emails/payment-reminder", context);
        sendEmail(to, "Payment Reminder | Gyantric CRM", body);
    }
}
