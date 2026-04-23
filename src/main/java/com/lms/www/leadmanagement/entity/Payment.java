package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_lead_id", columnList = "leadId"),
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_due_date", columnList = "dueDate"),
    @Index(name = "idx_payments_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long leadId;

    private BigDecimal amount; // Installment amount
    private BigDecimal totalAmount;
    private LocalDateTime date;
    private String paymentMethod; // UPI, CASH, CARD, BANK_TRANSFER
    private String paymentType; // FULL, EMI_INSTALLMENT
    private LocalDateTime dueDate;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private Status status;

    private String paymentGatewayId;
    private String note;
    private String receiptUrl; // For manual payment screenshots

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING, APPROVED, OVERDUE, PAID, FAILED, SUCCESS, CANCELLED
    }
}
