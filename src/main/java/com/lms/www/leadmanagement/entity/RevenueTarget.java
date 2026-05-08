package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "revenue_targets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user; // The manager or TL for whom target is set

    private Integer month; // 1-12
    private Integer year;
    
    private BigDecimal targetAmount;

    @Enumerated(EnumType.STRING)
    private TargetType type;

    @Column(name = "assigned_by")
    private Long assignedBy; // ID of the manager who assigned this

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
