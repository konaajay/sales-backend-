package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_shifts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Day Shift", "Morning Shift"

    @Column(nullable = false)
    private LocalTime startTime; // e.g., 09:00:00

    @Column(nullable = false)
    private LocalTime endTime; // e.g., 18:00:00

    private int graceMinutes; // e.g., 15 (User can check in until 09:15 without being late)

    private int minHalfDayMinutes; // e.g., 240 (4 hours)
    
    private int minFullDayMinutes; // e.g., 480 (8 hours)
    
    private LocalTime shortBreakStartTime;
    private LocalTime shortBreakEndTime;
    private LocalTime longBreakStartTime;
    private LocalTime longBreakEndTime;

    @ManyToOne
    @JoinColumn(name = "office_id")
    private OfficeLocation office; // Link shift to an office (Allows multiple shifts per office)
}
