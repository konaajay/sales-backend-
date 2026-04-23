package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attendance_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendancePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private OfficeLocation office;

    @Builder.Default
    private java.time.LocalTime shortBreakStartTime = java.time.LocalTime.of(17, 0);
    @Builder.Default
    private java.time.LocalTime shortBreakEndTime = java.time.LocalTime.of(17, 10);
    @Builder.Default
    private java.time.LocalTime longBreakStartTime = java.time.LocalTime.of(13, 0);
    @Builder.Default
    private java.time.LocalTime longBreakEndTime = java.time.LocalTime.of(14, 0);
    
    @Builder.Default
    private Integer gracePeriodMinutes = 2;

    @Builder.Default
    private Integer trackingIntervalSec = 300;
    @Builder.Default
    private Integer maxAccuracyMeters = 100;
    @Builder.Default
    private Integer minimumWorkMinutes = 240; // 4 Hours
    @Builder.Default
    private Integer maxIdleMinutes = 30; // Heartbeat failure timeout

    @Builder.Default
    private java.time.LocalTime shiftStartTime = java.time.LocalTime.of(9, 30);
}

