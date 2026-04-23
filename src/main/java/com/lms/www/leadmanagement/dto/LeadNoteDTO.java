package com.lms.www.leadmanagement.dto;

import com.lms.www.leadmanagement.entity.LeadNote;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadNoteDTO {
    private Long id;
    private String content;
    private String status;
    private String createdByName;
    private LocalDateTime createdAt;

    public static LeadNoteDTO fromEntity(LeadNote note) {
        if (note == null) return null;
        return LeadNoteDTO.builder()
                .id(note.getId())
                .content(note.getContent())
                .status(note.getStatus())
                .createdByName(note.getCreatedBy() != null ? note.getCreatedBy().getName() : "System")
                .createdAt(note.getCreatedAt())
                .build();
    }
}
