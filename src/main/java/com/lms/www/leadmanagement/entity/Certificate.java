package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificate")
@Data
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "submission_id")
    private Registration registration;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String webinarName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String certificateId;

    @Column(nullable = false)
    private String issueDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    private Integer retryCount = 0;
    private String storagePath;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (issueDate == null) {
            issueDate = LocalDate.now().toString();
        }
    }
}
