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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
