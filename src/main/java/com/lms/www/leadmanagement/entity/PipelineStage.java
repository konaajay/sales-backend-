package com.lms.www.leadmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String statusValue;
    private String label;
    private String color;
    private String analyticBucket;
    private int orderIndex;

    @Builder.Default
    @Column(name = "active")
    private boolean active = true;

    // Smart Behavior Config
    @Builder.Default
    private int defaultFollowupDays = 1;
    @Builder.Default
    private boolean requireNote = false;
    @Builder.Default
    private boolean requireDate = false;
    @Builder.Default
    private boolean createTask = false;
}
