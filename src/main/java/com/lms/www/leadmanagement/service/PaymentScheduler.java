package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class PaymentScheduler {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private MailService mailService;

    /**
     * Runs every hour to check for overdue payments.
     * A payment is OVERDUE if its status is PENDING and the dueDate has passed.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkOverduePayments() {
        List<Payment> pendingPayments = paymentRepository.findAllByStatus(Payment.Status.PENDING);
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        long count = 0;
        for (Payment payment : pendingPayments) {
            if (payment.getDueDate() != null && payment.getDueDate().isBefore(now)) {
                payment.setStatus(Payment.Status.OVERDUE);
                paymentRepository.save(payment);
                count++;

                // AUTOMATION: Send email to student
                try {
                    leadRepository.findById(payment.getLeadId()).ifPresent(lead -> {
                        if (lead.getEmail() != null) {
                            String formattedDate = payment.getDueDate().format(formatter);
                            mailService.sendOverdueReminder(
                                lead.getEmail(), 
                                lead.getName(), 
                                payment.getAmount(), 
                                formattedDate
                            );
                        }
                    });
                } catch (Exception e) {
                    log.error("FAILED to send automated overdue reminder for payment ID: {}", payment.getId(), e);
                }
            }
        }
        
        if (count > 0) {
            log.info("Marked {} payments as OVERDUE and triggered reminders", count);
        }
    }
}
