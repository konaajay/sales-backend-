package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "registrations")
@Data
public class Registration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "webinar_id", nullable = false)
    private Webinar webinar;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String collegeName;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String yearOfStudy;

    private String referralSource;
    private String crName;
    private String crPhone;
    private String friendName;
    private String friendPhone;
    private String friendCollege;

    private String certificateId;
    private boolean certificateSent;

    private LocalDateTime registrationDate;

    @PrePersist
    protected void onCreate() {
        registrationDate = LocalDateTime.now();
    }
}
