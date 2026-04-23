package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    private OfficeLocation office;

    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;
    
    // AutoCheckout flag to distinguish manual VS forced checkouts
    private boolean isAutoCheckout;

    // Track last known geodata
    private Double lastLat;
    private Double lastLng;
    private Double lastAccuracy;
    private LocalDateTime lastLocationTime;
    private LocalDateTime lastSeenTime; // Last valid inside reading

    // Fraud prevention and session safety
    private String deviceId;
    private String userAgent;
    private String ipHash;
    private String lastRequestId;
    
    private LocalDateTime outsideStartTime;
    private LocalDateTime breakStartTime; // For actual break tracking
    
    @Builder.Default
    private Integer totalWorkMinutes = 0;
    
    @Builder.Default
    private Integer totalBreakMinutes = 0;

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
