package com.lms.www.leadmanagement.util;

import com.lms.www.leadmanagement.dto.CertificateRequest;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CsvParser {

    public List<CertificateRequest> parseCsv(MultipartFile file, String webinarName) {
        List<CertificateRequest> requests = new ArrayList<>();
        
        log.info("Processing upload. Filename: {}, ContentType: {}, Size: {} bytes", 
                file.getOriginalFilename(), file.getContentType(), file.getSize());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // Read first line to detect delimiter
            reader.mark(1024);
            String firstLine = reader.readLine();
            reader.reset();
            
            if (firstLine == null) return requests;
            
            char delimiter = ',';
            if (firstLine.contains("\t")) delimiter = '\t';
            else if (firstLine.contains(";")) delimiter = ';';
            
            com.opencsv.CSVParser parser = new com.opencsv.CSVParserBuilder()
                    .withSeparator(delimiter)
                    .build();
            
            CSVReader csvReader = new com.opencsv.CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .build();
            
            String[] headers = csvReader.readNext();
            
            if (headers == null) return requests;

            // Map headers to indices (case-insensitive)
            int issueDateIdx = -1;
            int fullNameIdx = -1;
            int emailIdx = -1;       // Email Address column (fallback)
            int emailIdIdx = -1;     // EMAIL ID column (preferred)

            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].toLowerCase().replaceAll("\\s+", " ").trim().replace("\uFEFF", "");
                if (header.contains("timestamp") || header.contains("date")) issueDateIdx = i;
                else if (header.contains("full name") || header.contains("name for certificate") || header.equals("name")) fullNameIdx = i;
                else if (header.equals("email id") || header.equals("mail id")) emailIdIdx = i;  // exact EMAIL ID match — highest priority
                else if (header.contains("email") || header.contains("mail")) emailIdx = i;       // generic email — fallback
            }

            // Prefer EMAIL ID column; fall back to Email Address if not found
            int resolvedEmailIdx = (emailIdIdx != -1) ? emailIdIdx : emailIdx;
            emailIdx = resolvedEmailIdx;

            if (issueDateIdx == -1 || fullNameIdx == -1) {
                log.error("Missing required headers: Timestamp or FULL NAME FOR CERTIFICATE");
                return requests;
            }

            String[] record;
            while ((record = csvReader.readNext()) != null) {
                if (record.length <= Math.max(issueDateIdx, fullNameIdx)) continue;

                String issuedateRaw = record[issueDateIdx].trim();
                String fullname = record[fullNameIdx].trim();
                String email = (emailIdx != -1 && record.length > emailIdx) ? record[emailIdx].trim() : "";

                // Validation: fullname is null or empty → skip
                if (fullname.isEmpty()) {
                    log.warn("Skipping row: missing fullname");
                    continue;
                }

                // Validation: issuedate is null or empty → skip
                if (issuedateRaw.isEmpty()) {
                    log.warn("Skipping row: missing issuedate");
                    continue;
                }

                // Date Cleaning: Remove time from issuedate (e.g., 2026-05-01 10:30:45 -> 2026-05-01)
                String issuedate = issuedateRaw.split(" ")[0];

                CertificateRequest request = new CertificateRequest();
                request.setStudentName(fullname);
                request.setIssueDate(issuedate);
                request.setWebinarName(webinarName); // Provided by admin
                request.setEmail(email);
                
                requests.add(request);
            }
        } catch (Exception e) {
            log.error("CSV parsing failed for file {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new RuntimeException("Could not parse file: " + e.getMessage(), e);
        }
        
        log.info("Successfully parsed {} valid records from {}", requests.size(), file.getOriginalFilename());
        return requests;
    }

    private boolean isEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}
