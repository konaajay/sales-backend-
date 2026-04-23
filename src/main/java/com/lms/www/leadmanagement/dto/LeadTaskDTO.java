package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadTaskDTO {
    private Long id;
    private LeadDTO lead;
    private Long leadId;
    private String leadName;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private String status;
    private String taskType;
    private LocalDateTime createdAt;
}
