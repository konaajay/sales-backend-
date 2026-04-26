package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.BulkUploadResponseDTO;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class LeadBulkUploadService {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeadService leadService;

    @PreAuthorize("hasAuthority('CREATE_LEADS')")
    public BulkUploadResponseDTO uploadLeads(MultipartFile file, String assignedToIds) {
        int total = 0;
        int success = 0;
        int duplicates = 0;
        int failures = 0;
        List<String> errorList = new ArrayList<>();

        List<User> assignees = new ArrayList<>();
        if (assignedToIds != null && !assignedToIds.trim().isEmpty()) {
            for (String idStr : assignedToIds.split(",")) {
                try {
                    userRepository.findById(Long.parseLong(idStr.trim()))
                            .ifPresent(assignees::add);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        User creator = leadService.getCurrentUser();
        int assigneeIndex = 0;

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

                String[] data = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                if (data.length >= 2) {

                    String name = "";
                    String email = "";
                    String mobile = "";
                    String college = "";

                    if (data.length == 2) {
                        name = data[0].trim().replace("\"", "");
                        mobile = data[1].trim().replace("\"", "");
                    } else if (data.length == 3) {
                        name = data[0].trim().replace("\"", "");
                        email = data[1].trim().replace("\"", "");
                        mobile = data[2].trim().replace("\"", "");
                    } else if (data.length == 4) {
                        name = data[0].trim().replace("\"", "");
                        email = data[1].trim().replace("\"", "");
                        mobile = data[2].trim().replace("\"", "");
                        college = data[3].trim().replace("\"", "");
                    } else if (data.length >= 5) {
                        name = data[1].trim().replace("\"", "");
                        email = data[2].trim().replace("\"", "");
                        mobile = data[3].trim().replace("\"", "");
                        college = data[4].trim().replace("\"", "");
                    }

                    if (name.isEmpty() || mobile.isEmpty()) {
                        failures++;
                        errorList.add("Row " + rowNum + ": Name or Mobile is missing");
                        continue;
                    }

                    mobile = mobile.replaceAll("[^0-9]", "");
                    if (mobile.length() < 10) {
                        failures++;
                        errorList.add("Row " + rowNum + ": Invalid mobile number format");
                        continue;
                    }

                    // Round Robin Assignment
                    User finalAssignee;
                    if (!assignees.isEmpty()) {
                        finalAssignee = assignees.get(assigneeIndex % assignees.size());
                        assigneeIndex++;
                    } else {
                        // FORCE AUTO-ASSIGNMENT: Assign to self if no other assignees are specified
                        finalAssignee = creator;
                    }

                    // Duplicate Check
                    boolean isDuplicate = false;
                    if (!email.isEmpty() && leadRepository.existsByEmail(email)) {
                        isDuplicate = true;
                    } else if (leadRepository.existsByMobile(mobile)) {
                        isDuplicate = true;
                    }

                    if (isDuplicate) {
                        duplicates++;
                        errorList.add("Row " + rowNum + ": Mobile or Email already exists in system");
                        continue;
                    }

                    Lead lead = Lead.builder()
                            .name(name)
                            .email(email.isEmpty() ? null : email)
                            .mobile(mobile)
                            .college(college.isEmpty() ? null : college)

                            .status("WORKING")
                            .createdBy(creator)
                            .assignedTo(finalAssignee)
                            .build();
                    leadRepository.save(Objects.requireNonNull(lead));
                    success++;
                } else {
                    failures++;
                    errorList.add("Row " + rowNum + ": Insufficient columns (Expected Name, Email, Mobile)");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV Ingestion Interrupted: " + e.getMessage());
        }

        return BulkUploadResponseDTO.builder()
                .totalProcessed(total)
                .successCount(success)
                .duplicateCount(duplicates)
                .failureCount(failures)
                .errors(errorList)
                .build();
    }
}
