package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class DebugController {

    @Autowired
    private PaymentRepository paymentRepository;

    @GetMapping("/api/debug/pending-payments")
    public List<Payment> getPendingPayments() {
        return paymentRepository.findAllByStatus(Payment.Status.PENDING_APPROVAL);
    }
}
