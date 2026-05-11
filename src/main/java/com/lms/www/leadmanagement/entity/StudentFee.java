package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_fees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long leadId;

    private String studentName;
    private String studentEmail;
    private String studentMobile;

    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    private LocalDateTime nextDueDate;
    private String paymentStatus; 
    
    private Integer totalInstallments;
    private Integer paidInstallments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;
        if (balanceAmount == null) balanceAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        updateStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateStatus();
    }

    private void updateStatus() {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            paymentStatus = "DUE";
            return;
        }

        // 1. If pending amount is zero -> PAID
        if (balanceAmount != null && balanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            paymentStatus = "PAID";
        } 
        // 2. If no payments made yet -> DUE
        else if (paidInstallments == null || paidInstallments == 0) {
            paymentStatus = "DUE";
        } 
        // 3. If only initial commitment paid (and there are more to come) -> PRE_PAYMENT
        else if (paidInstallments == 1 && (totalInstallments != null && totalInstallments > 1)) {
            paymentStatus = "PRE_PAYMENT";
        } 
        // 4. Multiple installments or progress tracking -> POST_PAYMENT_X
        else {
            paymentStatus = "POST_PAYMENT_" + paidInstallments;
        }
    }
}
