package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvLeadRowDTO {
    private int rowNumber;
    private String name;
    private String email;
    private String mobile;
    private String courseName;
    private String assignedToEmail;
    private String teamLeaderEmail;
    private String status;
}
