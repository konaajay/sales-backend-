package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.BulkUploadResponseDTO;
import com.lms.www.leadmanagement.entity.CallRecord;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.CallRecordRepository;
import com.lms.www.leadmanagement.repository.LeadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CallBulkUploadService {

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private LeadService leadService;

    public BulkUploadResponseDTO uploadCallLogs(MultipartFile file) {
        int total = 0;
        int success = 0;
        int failures = 0;
        List<String> errorList = new ArrayList<>();
        User currentUser = leadService.getCurrentUser();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean firstLine = true;
            int rowNum = 0;

            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (line.trim().isEmpty())
                    continue;
                total++;

                // Regex to split by comma but ignore commas inside quotes
                String[] data = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                try {
                    if (data.length >= 3) {
                        String mobile = data[0].trim().replace("\"", "").replaceAll("[^0-9]", "");
                        String type = data[1].trim().replace("\"", "").toUpperCase(); // INCOMING / OUTGOING
                        String status = data[2].trim().replace("\"", "").toUpperCase(); // CONNECTED, BUSY, etc.

                        Integer duration = 0;
                        if (data.length >= 4) {
                            try {
                                duration = Integer.parseInt(data[3].trim());
                            } catch (NumberFormatException e) {
                                duration = 0;
                            }
                        }

                        String note = (data.length >= 5) ? data[4].trim().replace("\"", "") : "Bulk Uploaded";

                        LocalDateTime timestamp = LocalDateTime.now();
                        if (data.length >= 6) {
                            try {
                                timestamp = LocalDateTime.parse(data[5].trim().replace("\"", ""), formatter);
                            } catch (Exception e) {
                                // Default to now if format is wrong
                            }
                        }

                        if (mobile.length() < 10) {
                            failures++;
                            errorList.add("Row " + rowNum + ": Invalid mobile number");
                            continue;
                        }

                        // Try to find lead by mobile
                        Lead lead = leadRepository.findByMobile(mobile).orElse(null);

                        CallRecord record = CallRecord.builder()
                                .user(currentUser)
                                .lead(lead)
                                .phoneNumber(mobile)
                                .callType(type)
                                .status(status)
                                .notes(note)
                                .duration(duration)
                                .startTime(timestamp.minusSeconds(duration))
                                .endTime(timestamp)
                                .build();

                        if (record != null) {
                            callRecordRepository.save(record);
                        }
                        success++;
                    } else {
                        failures++;
                        errorList.add("Row " + rowNum
                                + ": Insufficient columns (Expected Mobile, Type, Status, [Duration], [Note], [Timestamp])");
                    }
                } catch (Exception e) {
                    failures++;
                    errorList.add("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Call Ingestion Failed: " + e.getMessage());
        }

        return BulkUploadResponseDTO.builder()
                .totalProcessed(total)
                .successCount(success)
                .duplicateCount(0)
                .failureCount(failures)
                .errors(errorList)
                .build();
    }
}
