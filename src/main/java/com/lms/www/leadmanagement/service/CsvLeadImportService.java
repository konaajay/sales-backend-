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

                // Ensure row has at least basic columns: name, email, mobile
                if (nextLine.length < 3) {
                    parsingFailures.add(FailedRow.builder()
                            .rowNumber(currentRowNum)
                            .name(nextLine.length > 0 ? nextLine[0] : "")
                            .email(nextLine.length > 1 ? nextLine[1] : "")
                            .mobile(nextLine.length > 2 ? nextLine[2] : "")
                            .reason("Malformed row: Insufficient columns (Expected at least Name, Email, Mobile)")
                            .build());
                    continue;
                }

                String name = nextLine[0] != null ? nextLine[0].trim() : "";
                String email = nextLine[1] != null ? nextLine[1].trim() : "";
                String mobile = nextLine[2] != null ? nextLine[2].trim() : "";
                String courseName = nextLine.length > 3 && nextLine[3] != null ? nextLine[3].trim() : "";
                String assignedToEmail = nextLine.length > 4 && nextLine[4] != null ? nextLine[4].trim() : "";
                String teamLeaderEmail = nextLine.length > 5 && nextLine[5] != null ? nextLine[5].trim() : "";
                String status = nextLine.length > 6 && nextLine[6] != null ? nextLine[6].trim() : "OLD_LEAD";

                CsvLeadRowDTO rowDTO = CsvLeadRowDTO.builder()
                        .rowNumber(currentRowNum)
                        .name(name)
                        .email(email)
                        .mobile(mobile)
                        .courseName(courseName)
                        .assignedToEmail(assignedToEmail)
                        .teamLeaderEmail(teamLeaderEmail)
                        .status(status)
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
