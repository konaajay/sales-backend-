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
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetailsImpl)) {
            throw new RuntimeException("Invalid authentication context");
        }
        return ((UserDetailsImpl) principal).getId();
    }

    @PostMapping("/clock-in")
    public ResponseEntity<ApiResponse<AttendanceDTO>> clockIn(@Valid @RequestBody LocationRequestDTO request, HttpServletRequest httpRequest) {
        request.setUserId(getCurrentUserId());
        String ua = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.clockIn(request, ua, ip)));
    }

    @PostMapping("/track")
    public ResponseEntity<ApiResponse<AttendanceDTO>> trackLocation(@Valid @RequestBody LocationRequestDTO request, HttpServletRequest httpRequest) {
        request.setUserId(getCurrentUserId());
        String ua = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.trackLocation(request, ua, ip)));
    }

    @PutMapping("/clock-out")
    public ResponseEntity<ApiResponse<AttendanceDTO>> clockOut() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.clockOut(getCurrentUserId())));
    }

    @PostMapping("/break/start")
    public ResponseEntity<ApiResponse<AttendanceDTO>> startBreak(@RequestParam(defaultValue = "SHORT") String type) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.startBreak(getCurrentUserId(), type)));
    }

    @PostMapping("/break/end")
    public ResponseEntity<ApiResponse<AttendanceDTO>> endBreak() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.endBreak(getCurrentUserId())));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<?>> getStatus() {
        Optional<AttendanceDTO> attendanceOpt = attendanceService.getCurrentStatus(getCurrentUserId());
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
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getMyLogs(getCurrentUserId())));
    }
}
