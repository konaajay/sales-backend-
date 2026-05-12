package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
public class PaymentSchedulerService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LeadPaymentService leadPaymentService;

    /**
     * Daily check for installments due today.
     * Runs at 10 AM daily.
     */
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional
    public void processDailyInstallmentReminders() {
        log.info(">>> Running Daily Installment Reminder Check at {}", LocalDateTime.now());
                LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime startRange = tomorrow.atStartOfDay();
        LocalDateTime endRange = tomorrow.atTime(LocalTime.MAX);

        // Find all PENDING installments due tomorrow (24 hours in advance)
        List<Payment> duePayments = paymentRepository.findByStatusInAndDueDateBetweenAndReminderSentFalse(
                List.of(Payment.Status.PENDING, Payment.Status.OVERDUE),
                startRange,
                endRange
        );

        int count = 0;
        for (Payment payment : duePayments) {
            try {
                leadPaymentService.sendInstallmentReminder(payment);
                count++;
            } catch (Exception e) {
                log.error(">>> Failed to send installment reminder for Payment ID {}: {}", payment.getId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info(">>> Successfully sent {} installment reminders today.", count);
        }
    }
    
    /**
     * Run every hour to catch any missed or overdue payments that still need a reminder.
     * This acts as a fallback for the daily job.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processOverdueInstallmentReminders() {
        LocalDateTime now = LocalDateTime.now();
        
        // Find installments due BEFORE now that still haven't had a reminder sent
        List<Payment> overduePayments = paymentRepository.findByStatusInAndDueDateBetweenAndReminderSentFalse(
                List.of(Payment.Status.PENDING, Payment.Status.OVERDUE),
                now.minusDays(7), // Don't go back forever
                now
        );

        for (Payment payment : overduePayments) {
            try {
                // Only send if it's been more than 24 hours since it was due and we haven't sent one
                leadPaymentService.sendInstallmentReminder(payment);
            } catch (Exception e) {
                log.error(">>> Fallback: Failed to send reminder for Payment ID {}: {}", payment.getId(), e.getMessage());
            }
        }
    }
}
