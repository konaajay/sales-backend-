package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.*;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import com.lms.www.leadmanagement.service.AttendanceService;
import com.lms.www.leadmanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserService userService;

    @PostMapping("/clock-in")
    public ResponseEntity<AttendanceDTO> clockIn(
            @RequestBody LocationRequestDTO request,
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest httpServletRequest) {
        request.setUserId(principal.getId());
        return ResponseEntity.ok(attendanceService.clockIn(request, 
            httpServletRequest.getHeader("User-Agent"), 
            httpServletRequest.getRemoteAddr()));
    }

    @PostMapping("/track")
    public ResponseEntity<AttendanceDTO> trackLocation(
            @RequestBody LocationRequestDTO request,
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest httpServletRequest) {
        request.setUserId(principal.getId());
        return ResponseEntity.ok(attendanceService.trackLocation(request,
            httpServletRequest.getHeader("User-Agent"),
            httpServletRequest.getRemoteAddr()));
    }

    @RequestMapping(value = "/clock-out", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<AttendanceDTO> clockOut(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(attendanceService.clockOut(principal.getId()));
    }

    @PostMapping("/break/start")
    public ResponseEntity<AttendanceDTO> startBreak(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam(defaultValue = "LUNCH") String type) {
        return ResponseEntity.ok(attendanceService.startBreak(principal.getId(), type));
    }

    @PostMapping("/break/end")
    public ResponseEntity<AttendanceDTO> endBreak(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(attendanceService.endBreak(principal.getId()));
    }

    @GetMapping("/status")
    public ResponseEntity<AttendanceDTO> getStatus(@AuthenticationPrincipal UserDetailsImpl principal) {
        return attendanceService.getCurrentStatus(principal.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(AttendanceDTO.builder()
                        .userId(principal.getId())
                        .status("NOT_STARTED")
                        .build()));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AttendanceDTO>> getLogs(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long managerId) {
        
        User requester = userService.findById(principal.getId());
        return ResponseEntity.ok(attendanceService.getLogs(start, end, userId, teamId, managerId, requester));
    }

    @GetMapping("/my-logs")
    public ResponseEntity<List<AttendanceDTO>> getMyLogs(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(attendanceService.getMyLogs(principal.getId(), from, to));
    }
}
