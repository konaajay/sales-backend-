package com.lms.www.leadmanagement.dto;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lms.www.leadmanagement.entity.Lead;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadDTO {
    private Long id;
    private String name;
    private String email;
    private String mobile;
    private String college;
    private Long courseId;
    private String courseName;

    private String status;
    private Long assignedToId;
    private String assignedToName;
    private Boolean followUpRequired;
    private LocalDateTime followUpDate;
    private String followUpType;
    private LocalDateTime nextPaymentDueDate;
    private String paymentStatus;
    private java.math.BigDecimal totalAmount;
    private java.math.BigDecimal paidAmount;
    private java.math.BigDecimal balanceAmount;
    private java.math.BigDecimal discount;
    private Integer totalInstallments;
    private Integer paidInstallments;
    @JsonProperty("hasOverdueTask")
    private boolean hasOverdueTask;
    @JsonProperty("taskDueToday")
    private boolean isTaskDueToday;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedById;
    private String updatedByName;
    private Long createdById;
    private String createdByName;
    private java.util.List<LeadNoteDTO> notes;

    public static LeadDTO fromEntity(Lead lead) {
        if (lead == null) return null;
        return LeadDTO.builder()
                .id(lead.getId())
                .name(lead.getName())
                .email(lead.getEmail())
                .mobile(lead.getMobile())
                .college(lead.getCollege())

                .status(lead.getStatus() != null ? lead.getStatus() : "NEW")
                .assignedToId(lead.getAssignedTo() != null ? lead.getAssignedTo().getId() : null)
                .assignedToName(lead.getAssignedTo() != null ? lead.getAssignedTo().getName() : null)
                .followUpRequired(lead.getFollowUpRequired())
                .followUpDate(lead.getFollowUpDate())
                .followUpType(lead.getFollowUpType())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .updatedById(lead.getUpdatedBy() != null ? lead.getUpdatedBy().getId() : null)
                .updatedByName(lead.getUpdatedBy() != null ? lead.getUpdatedBy().getName() : null)
                .createdById(lead.getCreatedBy() != null ? lead.getCreatedBy().getId() : null)
                .createdByName(lead.getCreatedBy() != null ? lead.getCreatedBy().getName() : null)
                .notes(lead.getNotes() != null ? 
                    lead.getNotes().stream().map(LeadNoteDTO::fromEntity).collect(java.util.stream.Collectors.toList()) : 
                    new java.util.ArrayList<>())
                .courseId(lead.getCourse() != null ? lead.getCourse().getId() : null)
                .courseName(lead.getCourse() != null ? lead.getCourse().getName() : null)
                .build();
    }
}
