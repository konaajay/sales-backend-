package com.lms.www.leadmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_daily")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDaily {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "subordinates", "managedAssociates", "directPermissions"})
    private User user;

    private LocalDate date;
    
    @Builder.Default
    private Integer totalWorkMinutes = 0;
    
    @Builder.Default
    private Integer totalBreakMinutes = 0;

    @Builder.Default
    private Long totalWorkSeconds = 0L;

    @Builder.Default
    private Long totalBreakSeconds = 0L;

    @Builder.Default
    private Long unauthorizedOutsideSeconds = 0L;

    @Builder.Default
    private Integer outsideCount = 0;
    
    @Builder.Default
    private String status = "ABSENT";

    @Builder.Default
    private boolean isLate = false;
    @Builder.Default
    private boolean isEarlyExit = false;
    @Builder.Default
    private int overtimeMinutes = 0;

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
