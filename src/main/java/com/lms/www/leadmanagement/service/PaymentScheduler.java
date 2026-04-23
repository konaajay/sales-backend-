package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class PaymentScheduler {

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * Runs every hour to check for overdue payments.
     * A payment is OVERDUE if its status is PENDING and the dueDate has passed.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkOverduePayments() {
        log.info(">>> Running Payment Overdue Check at {}", LocalDateTime.now());
        
        List<Payment> pendingPayments = paymentRepository.findAllByStatus(Payment.Status.PENDING);
        LocalDateTime now = LocalDateTime.now();
        
        long count = 0;
        for (Payment payment : pendingPayments) {
            if (payment.getDueDate() != null && payment.getDueDate().isBefore(now)) {
                payment.setStatus(Payment.Status.OVERDUE);
                paymentRepository.save(payment);
                count++;
            }
        }
        
        if (count > 0) {
            log.info(">>> Marked {} payments as OVERDUE", count);
        }
    }
}
