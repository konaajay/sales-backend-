package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.entity.CallRecord;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.CallRecordRepository;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CallLogService {

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.lms.www.leadmanagement.repository.LeadNoteRepository leadNoteRepository;

    @Autowired
    private SecurityService securityService;

    private static final String UPLOAD_DIR = "uploads/recordings/";
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    @Transactional
    public CallRecord saveCallRecord(Long userId, Long leadId, String phoneNumber, String callType,
            String status, String note, Integer duration,
            LocalDateTime clientStartTime,
            MultipartFile file) throws IOException {
        if (userId == null)
            throw new IllegalArgumentException("User ID cannot be null");
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Lead lead = leadId != null ? leadRepository.findById(leadId).orElse(null) : null;

        // 1. Storage Path: {cwd}/uploads/recordings/{userId}/{date}/
        String dateStr = LocalDate.now(INDIA_ZONE).toString();
        // Use absolute base to survive server restarts
        Path baseDir = Paths.get(System.getProperty("user.dir"), UPLOAD_DIR, String.valueOf(userId), dateStr);
        Files.createDirectories(baseDir);

        // 2. Determine extension — Chrome MediaRecorder sends 'video/webm' for
        // audio-only
        String ct = file.getContentType();
        String extension = getExtension(file.getOriginalFilename());
        if (("video/webm".equals(ct) || "audio/webm".equals(ct)) && !"webm".equalsIgnoreCase(extension)) {
            extension = "webm";
        }

        String filename = System.currentTimeMillis() + "_" + UUID.randomUUID() + "." + extension;
        Path filePath = baseDir.resolve(filename);

        // 3. Save file
        Files.copy(file.getInputStream(), filePath);

        try {
            LocalDateTime effectiveStart = clientStartTime != null ? clientStartTime
                    : LocalDateTime.now(INDIA_ZONE).minusSeconds(duration != null ? duration : 0);
            LocalDateTime effectiveEnd = clientStartTime != null
                    ? effectiveStart.plusSeconds(duration != null ? duration : 0)
                    : LocalDateTime.now(INDIA_ZONE);

            CallRecord callRecord = CallRecord.builder()
                    .user(user)
                    .lead(lead)
                    .phoneNumber(phoneNumber)
                    .callType(callType)
                    .status(status)
                    .notes(note)
                    .duration(duration)
                    .startTime(effectiveStart)
                    .endTime(effectiveEnd)
                    .recordingPath(filePath.toAbsolutePath().toString())
                    .build();

            // 4. Sync Lead State if associated
            if (lead != null && status != null && !status.isEmpty()) {
                try {
                    lead.setStatus(com.lms.www.leadmanagement.entity.LeadStatus.fromString(status));
                    lead.setName(note);
                    lead.setUpdatedBy(user);
                    leadRepository.save(lead);

                    // Add History Note
                    com.lms.www.leadmanagement.entity.LeadNote leadNote = com.lms.www.leadmanagement.entity.LeadNote
                            .builder()
                            .lead(lead)
                            .content("[Mobile Call Record] " + (note != null ? note : "No additional notes"))
                            .status(status)
                            .createdBy(user)
                            .createdAt(LocalDateTime.now(INDIA_ZONE))
                            .build();
                    leadNoteRepository.save(leadNote);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status received from mobile client: {}. Lead state not updated.", status);
                }
            }

            if (callRecord == null) {
                throw new RuntimeException("Failed to initialize interaction record");
            }
            return callRecordRepository.save(callRecord);
        } catch (Exception e) {
            Files.deleteIfExists(filePath);
            log.error("Rolling back audio file save for user {} due to DB error", userId);
            throw new RuntimeException("Failed to save record. File deleted for integrity.");
        }
    }

    @Transactional
    public void recordManualCall(User user, Lead lead, String status, String note) {
        CallRecord record = CallRecord.builder()
                .user(user)
                .lead(lead)
                .phoneNumber(lead != null ? lead.getMobile() : null)
                .callType("OUTGOING")
                .status(status)
                .notes(note)
                .startTime(LocalDateTime.now(INDIA_ZONE))
                .endTime(LocalDateTime.now(INDIA_ZONE))
                .build();
        callRecordRepository.save(record);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "mp3";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public List<CallRecord> getMyLogs(Long userId, LocalDate from, LocalDate to) {
        List<CallRecord> logs;
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            LocalDateTime end = (to != null ? to : from).atTime(23, 59, 59);
            logs = callRecordRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeDesc(userId, start, end);
        } else {
            logs = callRecordRepository.findByUserIdOrderByStartTimeDesc(userId);
        }
        // Initialize lazy proxies
        logs.forEach(this::initializeRecord);
        return logs;
    }

    public Map<String, Object> getStats(Long userId, LocalDate from, LocalDate to) {
        Map<String, Object> stats;
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            LocalDateTime end = (to != null ? to : from).atTime(23, 59, 59);
            stats = callRecordRepository.getStatsForUserByDate(userId, start, end);
        } else {
            stats = callRecordRepository.getStatsForUser(userId);
        }
        return processStats(stats);
    }

    public Map<String, Object> getGlobalStats(LocalDate from, LocalDate to) {
        Map<String, Object> stats;
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            LocalDateTime end = (to != null ? to : from).atTime(23, 59, 59);
            stats = callRecordRepository.getGlobalStatsByDate(start, end);
        } else {
            stats = callRecordRepository.getGlobalStats();
        }
        return processStats(stats);
    }

    private Map<String, Object> processStats(Map<String, Object> stats) {
        Map<String, Object> result = new HashMap<>();

        // Handle nulls correctly using a helper
        result.put("totalCalls", asLong(stats.get("totalCalls")));
        result.put("totalDuration", asLong(stats.get("totalDuration")));
        result.put("totalDurationFormatted", formatDuration(asLong(stats.get("totalDuration"))));

        result.put("incomingCount", asLong(stats.get("incomingCount")));
        result.put("incomingDuration", asLong(stats.get("incomingDuration")));
        result.put("incomingDurationFormatted", formatDuration(asLong(stats.get("incomingDuration"))));

        result.put("outgoingCount", asLong(stats.get("outgoingCount")));
        result.put("outgoingDuration", asLong(stats.get("outgoingDuration")));
        result.put("outgoingDurationFormatted", formatDuration(asLong(stats.get("outgoingDuration"))));

        result.put("missedCount", asLong(stats.get("missedCount")));
        result.put("rejectedCount", asLong(stats.get("rejectedCount")));
        result.put("neverAttendedCount", asLong(stats.get("neverAttendedCount")));
        result.put("notPickedCount", asLong(stats.get("notPickedCount")));
        result.put("uniqueCount", asLong(stats.get("uniqueCount")));

        return result;
    }

    private Long asLong(Object o) {
        if (o == null)
            return 0L;
        if (o instanceof Number)
            return ((Number) o).longValue();
        return 0L;
    }

    private String formatDuration(Long totalSeconds) {
        if (totalSeconds == null || totalSeconds == 0)
            return "0s";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0)
            sb.append(h).append("h ");
        if (m > 0)
            sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0)
            sb.append(s).append("s");
        return sb.toString().trim();
    }

    public Path getAudioFile(Long recordId, Long requestingUserId, boolean isAdmin) {
        CallRecord record = callRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Record not found"));

        if (!isAdmin && !record.getUser().getId().equals(requestingUserId)) {
            User requester = userRepository.findById(requestingUserId).orElseThrow();
            securityService.validateAccess(requester, record.getUser().getId());
        }

        String storedPath = record.getRecordingPath();
        // Handle both old relative paths and new absolute paths
        Path path = Paths.get(storedPath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(storedPath);
        }
        return path;
    }

    // --- Administrative Reporting ---

    @Transactional(readOnly = true)
    public List<CallRecord> getAllLogsAdmin(LocalDate from, LocalDate to, Long targetUserId, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("Requester not found"));
        
        java.util.Set<Long> allowedUserIds = securityService.getAllowedUserIds(requester);
        boolean isGlobalAdmin = securityService.isAdmin(requester) && targetUserId == null;

        if (targetUserId != null) {
            securityService.validateAccess(requester, targetUserId);
            allowedUserIds = java.util.Collections.singleton(targetUserId);
        }

        LocalDateTime start = (from != null) ? from.atStartOfDay() : LocalDateTime.now().minusYears(1);
        LocalDateTime end = (from != null) ? (to != null ? to : from).atTime(23, 59, 59) : LocalDateTime.now();

        List<CallRecord> logs;
        if (isGlobalAdmin) {
            logs = callRecordRepository.findByStartTimeBetweenOrderByStartTimeDesc(start, end);
        } else {
            logs = callRecordRepository.findByUserIdInAndStartTimeBetweenOrderByStartTimeDesc(new java.util.ArrayList<>(allowedUserIds),
                    start, end);
        }

        // Initialize lazy proxies to avoid serialization errors
        logs.forEach(this::initializeRecord);
        return logs;
    }

    private void initializeRecord(CallRecord record) {
        if (record.getUser() != null) {
            record.getUser().getName(); // Trigger proxy initialization
        }
        if (record.getLead() != null) {
            record.getLead().getName(); // Trigger proxy initialization
        }
    }

    public Map<String, Object> getGlobalStatsWithHierarchy(LocalDate from, LocalDate to, Long requesterId,
            Long targetUserId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("Requester not found"));

        java.util.Set<Long> userIds = securityService.getAllowedUserIds(requester);
        boolean isGlobalAdmin = securityService.isAdmin(requester) && targetUserId == null;

        if (targetUserId != null) {
            securityService.validateAccess(requester, targetUserId);
            userIds = java.util.Collections.singleton(targetUserId);
        }

        if (isGlobalAdmin) {
            return getGlobalStats(from, to);
        }

        LocalDateTime start = (from != null) ? from.atStartOfDay() : LocalDateTime.now().minusDays(365);
        LocalDateTime end = (to != null ? to : (from != null ? from : LocalDate.now())).atTime(23, 59, 59);

        Map<String, Object> stats = callRecordRepository.getStatsForUsersByDate(new java.util.ArrayList<>(userIds), start, end);
        return processStats(stats);
    }

}
