package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.entity.CallRecord;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import com.lms.www.leadmanagement.service.CallBulkUploadService;
import com.lms.www.leadmanagement.service.CallLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/call-records")
public class CallLogController {

    @Autowired
    private CallLogService callLogService;

    @Autowired
    private CallBulkUploadService bulkUploadService;

    @PostMapping("/bulk-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    public ResponseEntity<ApiResponse<?>> bulkUploadCalls(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(ApiResponse.success(bulkUploadService.uploadCallLogs(file)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetailsImpl)) {
            throw new RuntimeException("Invalid authentication context");
        }
        return ((UserDetailsImpl) principal).getId();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadRecording(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "leadId", required = false) Long leadId,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("callType") String callType,
            @RequestParam("status") String status,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam("duration") Integer duration,
            @RequestParam(value = "startTime", required = false) String startTimeStr) {

        try {
            java.time.LocalDateTime startTime = null;
            if (startTimeStr != null && !startTimeStr.isEmpty()) {
                try {
                    // Try parsing with T (Standard ISO)
                    startTime = java.time.LocalDateTime.parse(startTimeStr);
                } catch (Exception e) {
                    try {
                        // Fallback: Try parsing with space (Common for mobile)
                        startTime = java.time.LocalDateTime.parse(startTimeStr.replace(" ", "T"));
                    } catch (Exception ex) {
                        System.err.println("Failed to parse startTime from mobile: " + startTimeStr);
                    }
                }
            }
            String ct = file.getContentType();
            if (ct == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Missing content type"));
            }
            // Accept audio/* AND video/webm (Chrome MediaRecorder sends video/webm for audio-only)
            boolean allowed = ct.startsWith("audio/") || ct.equals("video/webm") || ct.equals("application/octet-stream");
            if (!allowed) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Only audio files are allowed. Received: " + ct));
            }
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(ApiResponse.error("File size must be < 50MB"));
            }

            CallRecord record = callLogService.saveCallRecord(
                getCurrentUserId(), leadId, phoneNumber, callType, status, note, duration, startTime, file
            );
            return ResponseEntity.ok(ApiResponse.success(record));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<CallRecord>>> getMyLogs() {
        try {
            List<CallRecord> logs = callLogService.getMyLogs(getCurrentUserId());
            return ResponseEntity.ok(ApiResponse.success(logs));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) java.time.LocalDate to) {
        try {
            Map<String, Object> stats = callLogService.getStats(getCurrentUserId(), from, to);
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<?> getAudio(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long userId = null;
            boolean isAdmin = false;
            if (principal instanceof UserDetailsImpl ud) {
                userId = ud.getId();
                isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                        .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                || a.getAuthority().equals("ROLE_MANAGER")
                                || a.getAuthority().equals("ROLE_TEAM_LEADER"));
            }

            Path path = callLogService.getAudioFile(id, userId, isAdmin);
            Resource resource = new UrlResource(java.util.Objects.requireNonNull(path.toUri()));

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String filename = path.getFileName().toString().toLowerCase();
            String contentType;
            if (filename.endsWith(".wav")) contentType = "audio/wav";
            else if (filename.endsWith(".ogg")) contentType = "audio/ogg";
            else if (filename.endsWith(".webm")) contentType = "audio/webm";
            else if (filename.endsWith(".m4a")) contentType = "audio/mp4";
            else contentType = "audio/mpeg";

            long fileLength = resource.contentLength();

            // Support Range requests so browsers can seek inside audio
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] parts = rangeHeader.substring(6).split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : fileLength - 1;
                end = Math.min(end, fileLength - 1);
                long contentLength = end - start + 1;

                try (java.io.InputStream stream = resource.getInputStream()) {
                    stream.skipNBytes(start);
                    byte[] data = stream.readNBytes((int) contentLength);
                    if (data == null) throw new RuntimeException("Failed to read audio data");
                    return ResponseEntity.status(org.springframework.http.HttpStatus.PARTIAL_CONTENT)
                            .contentType(java.util.Objects.requireNonNull(MediaType.parseMediaType(contentType)))
                            .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                            .body(new org.springframework.core.io.ByteArrayResource(data));
                }
            }

            return ResponseEntity.ok()
                    .contentType(java.util.Objects.requireNonNull(MediaType.parseMediaType(contentType)))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .contentType(java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // --- Administrative Reporting ---

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    public ResponseEntity<ApiResponse<List<CallRecord>>> getAllLogsAdmin(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Long userId) {
        try {
            // Diagnostic logging for 400 errors
            System.out.println("DEBUG: GET /admin/all - from=" + from + ", to=" + to + ", userId=" + userId);
            List<CallRecord> logs = callLogService.getAllLogsAdmin(from, to, userId, getCurrentUserId());
            return ResponseEntity.ok(ApiResponse.success(logs));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGlobalStats(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Long userId) {
        try {
            Map<String, Object> stats = callLogService.getGlobalStatsWithHierarchy(from, to, getCurrentUserId(), userId);
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }
}
