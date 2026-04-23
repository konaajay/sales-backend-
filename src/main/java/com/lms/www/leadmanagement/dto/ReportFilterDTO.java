package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFilterDTO {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long teamLeaderId;
    private Long userId;
    private String status;
}
