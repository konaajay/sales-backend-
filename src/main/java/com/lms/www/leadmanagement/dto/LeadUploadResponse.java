package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadUploadResponse {
    private int totalRows;
    private int successfulImports;
    private int failedImports;
    private String batchId;
    private List<FailedRow> failedRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRow {
        private int rowNumber;
        private String name;
        private String email;
        private String mobile;
        private String reason;
    }
}
