package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wfh_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WfhRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", columnDefinition = "DATE")
    private java.time.LocalDate startDate;

    @Column(name = "end_date", columnDefinition = "DATE")
    private java.time.LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "requested_days")
    @Builder.Default
    private Integer requestedDays = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_id")
    private User respondedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (startDate != null && endDate != null) {
            this.requestedDays = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }
    }

    // Helper to check if still valid
    public boolean isActiveOn(LocalDateTime now) {
        if (!"APPROVED".equals(status) || startDate == null || endDate == null) return false;
        java.time.LocalDate today = now.toLocalDate();
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }
}
