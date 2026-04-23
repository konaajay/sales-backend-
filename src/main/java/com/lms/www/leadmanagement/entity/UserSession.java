package com.lms.www.leadmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "subordinates", "managedAssociates", "directPermissions"})
    private User user;

    private LocalDateTime loginTime;
    private LocalDateTime lastActivity;
    private LocalDateTime logoutTime;
    
    @Column(length = 20)
    private String status; // "ACTIVE", "INACTIVE"
    
    private String ipAddress;
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        loginTime = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        lastActivity = loginTime;
        status = "ACTIVE";
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastActivity = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
    }
}
