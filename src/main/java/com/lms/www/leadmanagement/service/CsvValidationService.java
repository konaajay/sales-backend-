package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.CsvLeadRowDTO;
import com.lms.www.leadmanagement.dto.LeadUploadResponse.FailedRow;
import com.lms.www.leadmanagement.entity.Course;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.CourseRepository;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class CsvValidationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LeadRepository leadRepository;

    public ValidationResult validateBatch(List<CsvLeadRowDTO> rows) {
        List<ValidLeadMapping> validList = new ArrayList<>();
        List<FailedRow> failedList = new ArrayList<>();

        Set<String> batchMobiles = new HashSet<>();
        Set<String> batchEmails = new HashSet<>();

        // Pre-fetch caches for performance on large CSVs
        Map<String, User> userCache = new HashMap<>();
        Map<String, Course> courseCache = new HashMap<>();

        for (CsvLeadRowDTO row : rows) {
            int rNum = row.getRowNumber();

            // 1. Empty Row / Required Fields Check
            if (row.getName() == null || row.getName().trim().isEmpty()) {
                failedList.add(FailedRow.builder()
                        .rowNumber(rNum)
                        .name(row.getName())
                        .email(row.getEmail())
                        .mobile(row.getMobile())
                        .reason("Missing mandatory field (Name)")
                        .build());
                continue;
            }

            // 2. Phone Length & Format Check / Auto-generation
            String cleanMobile = "";
            if (row.getMobile() == null || row.getMobile().trim().isEmpty()) {
                // Generate a unique 10-digit number that starts with 9
                do {
                    cleanMobile = "9" + String.format("%09d", (long) (Math.random() * 1000000000L));
                } while (leadRepository.existsByMobile(cleanMobile) || batchMobiles.contains(cleanMobile));
            } else {
                cleanMobile = row.getMobile().replaceAll("[^0-9]", "");
                if (cleanMobile.length() < 10) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Invalid mobile number length (must be at least 10 digits)")
                            .build());
                    continue;
                }
            }

            // 3. Duplicate Mobile Check (Batch level & DB level)
            if (batchMobiles.contains(cleanMobile)) {
                failedList.add(FailedRow.builder()
                        .rowNumber(rNum)
                        .name(row.getName())
                        .email(row.getEmail())
                        .mobile(row.getMobile())
                        .reason("Duplicate mobile number within the CSV batch")
                        .build());
                continue;
            }
            if (leadRepository.existsByMobile(cleanMobile)) {
                failedList.add(FailedRow.builder()
                        .rowNumber(rNum)
                        .name(row.getName())
                        .email(row.getEmail())
                        .mobile(row.getMobile())
                        .reason("Mobile number already exists in the database")
                        .build());
                continue;
            }

            // 4. Duplicate Email Check (Batch level & DB level)
            String cleanEmail = row.getEmail() != null ? row.getEmail().trim() : "";
            if (!cleanEmail.isEmpty()) {
                if (batchEmails.contains(cleanEmail.toLowerCase())) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Duplicate email address within the CSV batch")
                            .build());
                    continue;
                }
                if (leadRepository.existsByEmail(cleanEmail)) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Email address already exists in the database")
                            .build());
                    continue;
                }
            }

            // 5. Course Validation
            String courseVal = row.getCourseName() != null ? row.getCourseName().trim() : "";
            Course course = null;
            if (!courseVal.isEmpty()) {
                course = courseCache.computeIfAbsent(courseVal.toLowerCase(), k -> {
                    try {
                        Long id = Long.parseLong(courseVal);
                        return courseRepository.findById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return courseRepository.findByName(courseVal).orElse(null);
                    }
                });
                if (course == null) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Invalid course: '" + courseVal + "' (ID or Name not found)")
                            .build());
                    continue;
                }
            }

            // 6. User & TL Validation
            String assignedVal = row.getAssignedToEmail() != null ? row.getAssignedToEmail().trim() : "";
            String tlVal = row.getTeamLeaderEmail() != null ? row.getTeamLeaderEmail().trim() : "";
            String managerVal = row.getManagerEmail() != null ? row.getManagerEmail().trim() : "";

            User assignedUser = null;
            if (!assignedVal.isEmpty()) {
                assignedUser = userCache.computeIfAbsent(assignedVal.toLowerCase(), k -> {
                    try {
                        Long id = Long.parseLong(assignedVal);
                        return userRepository.findById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return userRepository.findByEmail(assignedVal)
                                .orElseGet(() -> userRepository.findByNameIgnoreCase(assignedVal).orElse(null));
                    }
                });
                if (assignedUser == null) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Assigned user '" + assignedVal + "' (ID, Email, or Name not found in system)")
                            .build());
                    continue;
                }
            }

            User teamLeader = null;
            if (!tlVal.isEmpty()) {
                teamLeader = userCache.computeIfAbsent(tlVal.toLowerCase(), k -> {
                    try {
                        Long id = Long.parseLong(tlVal);
                        return userRepository.findById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return userRepository.findByEmail(tlVal)
                                .orElseGet(() -> userRepository.findByNameIgnoreCase(tlVal).orElse(null));
                    }
                });
                if (teamLeader == null) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Team Leader '" + tlVal + "' (ID, Email, or Name not found in system)")
                            .build());
                    continue;
                }
            }

            User manager = null;
            if (!managerVal.isEmpty()) {
                manager = userCache.computeIfAbsent(managerVal.toLowerCase(), k -> {
                    try {
                        Long id = Long.parseLong(managerVal);
                        return userRepository.findById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return userRepository.findByEmail(managerVal)
                                .orElseGet(() -> userRepository.findByNameIgnoreCase(managerVal).orElse(null));
                    }
                });
                if (manager == null) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Manager '" + managerVal + "' (ID, Email, or Name not found in system)")
                            .build());
                    continue;
                }
            }

            // 7. Hierarchy Validation (Relaxed)
            // We are skipping the strict checks that verify if the associate is officially 
            // reporting to the exact team leader/manager in their user profile. 
            // As long as the emails provided in the CSV belong to valid users in the system, we accept the assignment.

            // Passed all validations!
            batchMobiles.add(cleanMobile);
            if (!cleanEmail.isEmpty()) {
                batchEmails.add(cleanEmail.toLowerCase());
            }

            validList.add(new ValidLeadMapping(row, cleanMobile, cleanEmail, course, assignedUser, teamLeader, manager));
        }

        return new ValidationResult(validList, failedList);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ValidLeadMapping {
        private CsvLeadRowDTO rawRow;
        private String cleanMobile;
        private String cleanEmail;
        private Course course;
        private User assignedUser;
        private User teamLeader;
        private User manager;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private List<ValidLeadMapping> validLeads;
        private List<FailedRow> failedRows;
    }
}
