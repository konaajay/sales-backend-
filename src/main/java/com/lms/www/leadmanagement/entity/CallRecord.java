package com.lms.www.leadmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "call_records", indexes = {
        @Index(name = "idx_call_user_time", columnList = "user_id, startTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "calls" })
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "password", "subordinates", "managedAssociates",
            "directPermissions" })
    private User user;

    private String phoneNumber;

    // Values: "INCOMING", "OUTGOING"
    private String callType;

    // Status like "FOLLOW_UP", "INTERESTED", etc.
    private String status;

    private String notes;
    private LocalDateTime followUpDate;

    private String recordingPath;

    private Integer duration; // in seconds

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private LocalDateTime createdAt;

    @PrePersist

    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
