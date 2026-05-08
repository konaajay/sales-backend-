package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.dto.CallTrackingEndDTO;
import com.lms.www.leadmanagement.dto.CallTrackingStartDTO;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import com.lms.www.leadmanagement.service.CallTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calls")
public class CallTrackingController {

    @Autowired
    private CallTrackingService callTrackingService;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetailsImpl)) {
            throw new RuntimeException("Invalid authentication context");
        }
        return ((UserDetailsImpl) principal).getId();
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<?>> startCall(@RequestBody CallTrackingStartDTO dto) {
        try {
            return ResponseEntity.ok(ApiResponse.success(callTrackingService.startCall(getCurrentUserId(), dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/end/{callId}")
    public ResponseEntity<ApiResponse<?>> endCall(@PathVariable Long callId, @RequestBody CallTrackingEndDTO dto) {
        try {
            return ResponseEntity.ok(ApiResponse.success(callTrackingService.endCall(getCurrentUserId(), callId, dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
    public ResponseEntity<ApiResponse<?>> getTodayReport() {
        try {
            return ResponseEntity.ok(ApiResponse.success(callTrackingService.getTodayReport()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
