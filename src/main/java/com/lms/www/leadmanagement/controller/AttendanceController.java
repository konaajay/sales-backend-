package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.LocationRequestDTO;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import com.lms.www.leadmanagement.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            System.err.println(">>> ATTENDANCE ERROR: Not authenticated");
            return null; // Let the caller handle or return 401
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetailsImpl)) {
            System.err.println(">>> ATTENDANCE ERROR: Principal is not UserDetailsImpl: " + (principal != null ? principal.getClass().getName() : "null"));
            return null;
        }
        return ((UserDetailsImpl) principal).getId();
    }

    @PostMapping("/clock-in")
    public ResponseEntity<ApiResponse<AttendanceDTO>> clockIn(@Valid @RequestBody LocationRequestDTO request, HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(ApiResponse.error("AUTHENTICATION_REQUIRED: Please login again"));
        request.setUserId(userId);
        String ua = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.clockIn(request, ua, ip)));
    }

    @PostMapping("/track")
    public ResponseEntity<ApiResponse<AttendanceDTO>> trackLocation(@Valid @RequestBody LocationRequestDTO request, HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(ApiResponse.error("AUTHENTICATION_REQUIRED: Session expired"));
        request.setUserId(userId);
        String ua = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.trackLocation(request, ua, ip)));
    }

    @PutMapping("/clock-out")
    public ResponseEntity<ApiResponse<AttendanceDTO>> clockOut() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.clockOut(userId)));
    }

    @PostMapping("/break/start")
    public ResponseEntity<ApiResponse<AttendanceDTO>> startBreak(@RequestParam(defaultValue = "SHORT") String type) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.startBreak(userId, type)));
    }

    @PostMapping("/break/end")
    public ResponseEntity<ApiResponse<AttendanceDTO>> endBreak() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.endBreak(userId)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<?>> getStatus() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        Optional<AttendanceDTO> attendanceOpt = attendanceService.getCurrentStatus(userId);
        if (attendanceOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(attendanceOpt.get()));
        } else {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "NOT_STARTED",
                "totalWorkMinutes", 0,
                "totalBreakMinutes", 0,
                "totalWorkHours", "0h 0m"
            )));
        }
    }

    @GetMapping("/my-logs")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getMyLogs() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getMyLogs(userId)));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<com.lms.www.leadmanagement.dto.AttendancePreviewResponse>> preview(@RequestBody com.lms.www.leadmanagement.dto.AttendancePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.calculatePreview(request)));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<String>> saveManual(@RequestBody com.lms.www.leadmanagement.dto.AttendancePreviewRequest request) {
        attendanceService.saveManualEntry(request);
        return ResponseEntity.ok(ApiResponse.success("Attendance entry synchronized successfully"));
    }
}
