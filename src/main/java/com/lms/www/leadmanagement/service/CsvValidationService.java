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
            if (row.getName() == null || row.getName().trim().isEmpty() ||
                row.getMobile() == null || row.getMobile().trim().isEmpty()) {
                failedList.add(FailedRow.builder()
                        .rowNumber(rNum)
                        .name(row.getName())
                        .email(row.getEmail())
                        .mobile(row.getMobile())
                        .reason("Missing mandatory fields (Name or Mobile)")
                        .build());
                continue;
            }

            // 2. Phone Length & Format Check
            String cleanMobile = row.getMobile().replaceAll("[^0-9]", "");
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

            User assignedUser = null;
            if (!assignedVal.isEmpty()) {
                assignedUser = userCache.computeIfAbsent(assignedVal.toLowerCase(), k -> {
                    try {
                        Long id = Long.parseLong(assignedVal);
                        return userRepository.findById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return userRepository.findByEmail(assignedVal).orElse(null);
                    }
                });
                if (assignedUser == null) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Assigned user '" + assignedVal + "' (ID or Email not found in system)")
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
                        return userRepository.findByEmail(tlVal).orElse(null);
                    }
                });
                if (teamLeader == null) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Team Leader '" + tlVal + "' (ID or Email not found in system)")
                            .build());
                    continue;
                }
            }

            // 7. TL Mapping Validation (If both assignedUser and teamLeader exist)
            if (assignedUser != null && teamLeader != null) {
                // Verify if assignedUser's supervisor or manager matches teamLeader
                boolean isValidTl = (assignedUser.getSupervisor() != null && assignedUser.getSupervisor().getId().equals(teamLeader.getId())) ||
                                    (assignedUser.getManager() != null && assignedUser.getManager().getId().equals(teamLeader.getId()));
                if (!isValidTl) {
                    failedList.add(FailedRow.builder()
                            .rowNumber(rNum)
                            .name(row.getName())
                            .email(row.getEmail())
                            .mobile(row.getMobile())
                            .reason("Invalid hierarchy: User '" + assignedVal + "' is not assigned under Team Leader '" + tlVal + "'")
                            .build());
                    continue;
                }
            }

            // Passed all validations!
            batchMobiles.add(cleanMobile);
            if (!cleanEmail.isEmpty()) {
                batchEmails.add(cleanEmail.toLowerCase());
            }

            validList.add(new ValidLeadMapping(row, cleanMobile, cleanEmail, course, assignedUser, teamLeader));
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
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private List<ValidLeadMapping> validLeads;
        private List<FailedRow> failedRows;
    }
}
