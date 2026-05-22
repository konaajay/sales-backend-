package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.CsvLeadRowDTO;
import com.lms.www.leadmanagement.dto.LeadUploadResponse;
import com.lms.www.leadmanagement.dto.LeadUploadResponse.FailedRow;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.service.CsvValidationService.ValidationResult;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CsvLeadImportService {

    @Autowired
    private CsvValidationService csvValidationService;

    @Autowired
    private LeadAssignmentService leadAssignmentService;

    @Autowired
    private LeadRealtimeNotificationService leadRealtimeNotificationService;

    public LeadUploadResponse importOldLeadsCsv(MultipartFile file) {
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Starting CSV import for old leads. Batch ID: {}", batchId);

        List<CsvLeadRowDTO> parsedRows = new ArrayList<>();
        List<FailedRow> parsingFailures = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVReader csvReader = new CSVReaderBuilder(fileReader).withSkipLines(1).build()) {

            String[] nextLine;
            int currentRowNum = 1; // Header is line 1, first data row is line 2

            while ((nextLine = csvReader.readNext()) != null) {
                currentRowNum++;
                totalRows++;

                // Ensure row has at least basic columns
                if (nextLine.length < 2) {
                    parsingFailures.add(FailedRow.builder()
                            .rowNumber(currentRowNum)
                            .name(nextLine.length > 0 ? nextLine[0] : "")
                            .email(nextLine.length > 1 ? nextLine[1] : "")
                            .reason("Malformed row: Insufficient columns (Expected at least Date and Name)")
                            .build());
                    continue;
                }

                // Read columns based on layout length dynamically
                String createdDate = "";
                String name = "";
                String courseName = "";
                String email = "";
                String mobile = "";
                String assignedToEmail = "";
                String teamLeaderEmail = "";
                String managerEmail = "";
                String totalFee = "";
                String paidAmount = "";
                String pendingAmount = "";
                String paymentMode = "";
                String paymentDate = "";
                String paymentType = "";
                String followUpDate = "";
                String status = "OLD_LEAD";
                String remark = "";

                if (nextLine.length == 13) {
                    // Old 13-column layout
                    createdDate = nextLine[0] != null ? nextLine[0].trim() : "";
                    name = nextLine.length > 1 && nextLine[1] != null ? nextLine[1].trim() : "";
                    courseName = nextLine.length > 2 && nextLine[2] != null ? nextLine[2].trim() : "";
                    email = nextLine.length > 3 && nextLine[3] != null ? nextLine[3].trim() : "";
                    mobile = ""; // No mobile column in this format, auto-generated in validation
                    assignedToEmail = nextLine.length > 4 && nextLine[4] != null ? nextLine[4].trim() : "";
                    totalFee = nextLine.length > 5 && nextLine[5] != null ? nextLine[5].trim() : "";
                    paidAmount = nextLine.length > 6 && nextLine[6] != null ? nextLine[6].trim() : "";
                    pendingAmount = nextLine.length > 7 && nextLine[7] != null ? nextLine[7].trim() : "";
                    paymentMode = nextLine.length > 8 && nextLine[8] != null ? nextLine[8].trim() : "";
                    paymentDate = createdDate; // Reuse createdDate as paymentDate
                    paymentType = nextLine.length > 9 && nextLine[9] != null ? nextLine[9].trim() : "";
                    followUpDate = nextLine.length > 10 && nextLine[10] != null ? nextLine[10].trim() : "";
                    status = nextLine.length > 11 && nextLine[11] != null ? nextLine[11].trim() : "OLD_LEAD";
                    remark = nextLine.length > 12 && nextLine[12] != null ? nextLine[12].trim() : "";
                } else {
                    // New/explicit 17-column layout (default)
                    createdDate = nextLine[0] != null ? nextLine[0].trim() : "";
                    name = nextLine.length > 1 && nextLine[1] != null ? nextLine[1].trim() : "";
                    courseName = nextLine.length > 2 && nextLine[2] != null ? nextLine[2].trim() : "";
                    email = nextLine.length > 3 && nextLine[3] != null ? nextLine[3].trim() : "";
                    mobile = nextLine.length > 4 && nextLine[4] != null ? nextLine[4].trim() : "";
                    assignedToEmail = nextLine.length > 5 && nextLine[5] != null ? nextLine[5].trim() : "";
                    teamLeaderEmail = nextLine.length > 6 && nextLine[6] != null ? nextLine[6].trim() : "";
                    managerEmail = nextLine.length > 7 && nextLine[7] != null ? nextLine[7].trim() : "";
                    totalFee = nextLine.length > 8 && nextLine[8] != null ? nextLine[8].trim() : "";
                    paidAmount = nextLine.length > 9 && nextLine[9] != null ? nextLine[9].trim() : "";
                    pendingAmount = nextLine.length > 10 && nextLine[10] != null ? nextLine[10].trim() : "";
                    paymentMode = nextLine.length > 11 && nextLine[11] != null ? nextLine[11].trim() : "";
                    paymentDate = nextLine.length > 12 && nextLine[12] != null ? nextLine[12].trim() : "";
                    paymentType = nextLine.length > 13 && nextLine[13] != null ? nextLine[13].trim() : "";
                    followUpDate = nextLine.length > 14 && nextLine[14] != null ? nextLine[14].trim() : "";
                    status = nextLine.length > 15 && nextLine[15] != null ? nextLine[15].trim() : "OLD_LEAD";
                    remark = nextLine.length > 16 && nextLine[16] != null ? nextLine[16].trim() : "";
                }

                CsvLeadRowDTO rowDTO = CsvLeadRowDTO.builder()
                        .rowNumber(currentRowNum)
                        .name(name)
                        .email(email)
                        .mobile(mobile)
                        .courseName(courseName)
                        .assignedToEmail(assignedToEmail)
                        .teamLeaderEmail(teamLeaderEmail)
                        .status(status)
                        .managerEmail(managerEmail)
                        .remark(remark)
                        .totalFee(totalFee)
                        .paidAmount(paidAmount)
                        .pendingAmount(pendingAmount)
                        .paymentMode(paymentMode)
                        .paymentDate(paymentDate)
                        .paymentType(paymentType)
                        .createdDate(createdDate)
                        .followUpDate(followUpDate)
                        .build();

                parsedRows.add(rowDTO);
            }

        } catch (Exception e) {
            log.error("Error reading CSV file for batch {}: {}", batchId, e.getMessage(), e);
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage());
        }

        // 2. Validate parsed rows
        ValidationResult validationResult = csvValidationService.validateBatch(parsedRows);

        // Combine parsing failures with validation failures
        List<FailedRow> allFailures = new ArrayList<>(parsingFailures);
        allFailures.addAll(validationResult.getFailedRows());

        // 3. Map users and batch save valid leads
        List<Lead> savedLeads = new ArrayList<>();
        if (!validationResult.getValidLeads().isEmpty()) {
            savedLeads = leadAssignmentService.assignAndBatchSave(validationResult.getValidLeads(), batchId);
        }

        int successCount = savedLeads.size();
        int failureCount = allFailures.size();

        // 4. Real-time broadcast notification
        if (successCount > 0) {
            leadRealtimeNotificationService.notifyLeadBatchImported(batchId, successCount);
        }

        log.info("Completed CSV import for batch {}. Total: {}, Success: {}, Failed: {}", batchId, totalRows, successCount, failureCount);

        return LeadUploadResponse.builder()
                .totalRows(totalRows)
                .successfulImports(successCount)
                .failedImports(failureCount)
                .batchId(batchId)
                .failedRows(allFailures)
                .build();
    }
}
