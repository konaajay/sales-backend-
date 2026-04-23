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
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    private LocalDateTime nextDueDate;
    private String paymentStatus; // PENDING, PARTIAL, COMPLETED

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
            paymentStatus = "AUDIT";
            return;
        }
        if (paidAmount.compareTo(totalAmount) >= 0) {
            paymentStatus = "COMPLETED";
        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = "PARTIAL";
        } else {
            paymentStatus = "PENDING";
        }
    }
}
