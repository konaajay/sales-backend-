package com.lms.www.leadmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions", indexes = {
    @Index(name = "idx_user_status", columnList = "user_id, status"),
    @Index(name = "idx_user_checkin", columnList = "user_id, check_in_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "subordinates", "managedAssociates", "directPermissions", "manager", "supervisor"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private OfficeLocation office;

    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
    
    @Builder.Default
    @Column(name = "auto_checkout", nullable = false)
    private boolean isAutoCheckout = false;

    // Core tracking fields
    private Double lastLat;
    private Double lastLng;
    private LocalDateTime lastSeenTime;

    // Time Accumulators (in seconds)
    @Builder.Default
    private Long totalWorkSeconds = 0L;

    @Builder.Default
    private Long totalBreakSeconds = 0L;

    @Builder.Default
    private Long shortBreakSeconds = 0L;

    @Builder.Default
    private Long longBreakSeconds = 0L;

    @Builder.Default
    private Long totalOutsideSeconds = 0L;

    // Late Details
    @Builder.Default
    @Column(name = "late", nullable = false)
    private boolean isLate = false;
    
    @Builder.Default
    private Integer lateMinutes = 0;

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
