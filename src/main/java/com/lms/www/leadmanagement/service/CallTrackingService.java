package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.*;
import com.lms.www.leadmanagement.entity.CallRecord;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.CallRecordRepository;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class CallTrackingService {

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeadRepository leadRepository;
    
    @Autowired
    private com.lms.www.leadmanagement.repository.LeadNoteRepository leadNoteRepository;

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    @Transactional
    public CallRecord startCall(Long userId, CallTrackingStartDTO dto) {
        // Block if already an active call
        callRecordRepository.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(userId)
            .ifPresent(call -> {
                throw new RuntimeException("An active interaction is already in progress. Terminate current session first.");
            });

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Lead lead = dto.getLeadId() != null ? leadRepository.findById(dto.getLeadId()).orElse(null) : null;

        CallRecord record = CallRecord.builder()
                .user(user)
                .lead(lead)
                .phoneNumber(dto.getPhoneNumber())
                .callType("OUTGOING")
                .startTime(LocalDateTime.now(INDIA_ZONE))
                .status("STARTED")
                .build();
                
        return callRecordRepository.save(record);
    }

    @Transactional
    public CallRecord endCall(Long userId, Long callId, CallTrackingEndDTO dto) {
        CallRecord record = callRecordRepository.findById(callId)
            .orElseThrow(() -> new RuntimeException("Interaction record not found"));

        if (!record.getUser().getId().equals(userId)) {
            throw new RuntimeException("Identity mismatch. Unauthorized terminal access.");
        }

        if (record.getEndTime() != null) {
            throw new RuntimeException("Interaction already terminated.");
        }

        // Strict Validation (Backend Controlled)
        if (dto.getStatus() == null || dto.getStatus().trim().isEmpty()) {
            throw new RuntimeException("Status override is mandatory for synchronization.");
        }
        if (dto.getNotes() == null || dto.getNotes().trim().length() < 10) {
            throw new RuntimeException("Observations must be at least 10 characters for valid logging.");
        }

        LocalDateTime now = LocalDateTime.now(INDIA_ZONE);
        long durationSecs = Duration.between(record.getStartTime(), now).getSeconds();

        // Anti-Cheat: Reject calls with duration < 20 sec
        if (durationSecs < 20) {
            record.setEndTime(now);
            record.setDuration((int) durationSecs);
            record.setStatus("INVALID_SHORT_CALL");
            record.setNotes("[REJECTED] Call too short: " + dto.getNotes());
            callRecordRepository.save(record);
            throw new RuntimeException("Security Protocol: Interaction duration (< 20s) insufficient for valid log.");
        }

        record.setEndTime(now);
        record.setDuration((int) durationSecs);
        record.setStatus(dto.getStatus());
        record.setNotes(dto.getNotes());
        
        if (dto.getFollowUpDate() != null && !dto.getFollowUpDate().isEmpty()) {
            try {
                record.setFollowUpDate(LocalDateTime.parse(dto.getFollowUpDate()));
            } catch (Exception e) {
                // Ignore parse errors for redundant field
            }
        }

        // Update Lead state and Follow-up
        if (record.getLead() != null) {
            Lead lead = record.getLead();
            try {
                lead.setStatus(dto.getStatus().toUpperCase());
            } catch (Exception e) {
                // Ignore invalid status enum mappings if they don't match exactly
            }
            lead.setNote(dto.getNotes());
            lead.setUpdatedBy(record.getUser());
            
            if (dto.getFollowUpDate() != null && !dto.getFollowUpDate().isEmpty()) {
                try {
                    lead.setFollowUpDate(LocalDateTime.parse(dto.getFollowUpDate()));
                    lead.setFollowUpRequired(true);
                } catch (Exception e) {
                    // Log date parse error or handle as needed
                }
            }
            
            leadRepository.save(lead);
            
            // Log entry in lead history
            com.lms.www.leadmanagement.entity.LeadNote leadNote = com.lms.www.leadmanagement.entity.LeadNote.builder()
                    .lead(lead)
                    .content("[Interaction Log] " + dto.getNotes() + " (Duration: " + durationSecs + "s)")
                    .status(dto.getStatus())
                    .createdBy(record.getUser())
                    .createdAt(now)
                    .build();
            leadNoteRepository.save(leadNote);
        }

        return callRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public DailyReportDTO getTodayReport() {
        LocalDateTime startOfDay = LocalDate.now(INDIA_ZONE).atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now(INDIA_ZONE).atTime(23, 59, 59);

        List<DailyUserReportDTO> userReports = callRecordRepository.getDailyUserReports(startOfDay, endOfDay);
        
        long totalCalls = userReports.stream().mapToLong(DailyUserReportDTO::getCallCount).sum();
        long totalDuration = userReports.stream().mapToLong(DailyUserReportDTO::getTotalDuration).sum();

        // Add suspicious flagging logic
        userReports.forEach(report -> {
            if (report.getCallCount() > 0 && (report.getTotalDuration() / report.getCallCount()) < 30) {
                report.setFlagged(true);
                report.setSuspicionReason("High frequency of short duration interactions");
            }
        });

        return DailyReportDTO.builder()
                .totalCalls(totalCalls)
                .totalDuration(totalDuration)
                .userReports(userReports)
                .build();
    }
}
