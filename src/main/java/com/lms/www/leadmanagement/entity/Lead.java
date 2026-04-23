package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "leads", indexes = {
    @Index(name = "idx_leads_assigned_to", columnList = "assigned_to"),
    @Index(name = "idx_leads_created_at", columnList = "createdAt"),
    @Index(name = "idx_leads_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    @Column(unique = true, nullable = false)
    private String mobile;

    private String college;
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private Status status;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    private String paymentLink;

    private String paymentOrderId;

    private String paymentSessionId;

    private String rejectionReason;
    private String rejectionNote;
    private Boolean followUpRequired;
    private LocalDateTime followUpDate;
    private String followUpType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<LeadNote> notes = new java.util.ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.NEW;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        NEW, CONTACTED, INTERESTED, UNDER_REVIEW, CONVERTED, LOST, NOT_INTERESTED, PAID, PAYMENT_FAILED, FOLLOW_UP, EMI, CLOSED, RETRY, WORKING, PENDING_MESSAGES, SUCCESS
    }
}
