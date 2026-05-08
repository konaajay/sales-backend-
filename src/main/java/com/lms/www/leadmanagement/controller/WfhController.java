package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.WfhRequest;
import com.lms.www.leadmanagement.repository.WfhRequestRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance/wfh")
@RequiredArgsConstructor
public class WfhController {

    private final WfhRequestRepository wfhRequestRepository;
    private final UserRepository userRepository;
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    @PostMapping("/request")
    @Transactional
    public ResponseEntity<?> requestWfh(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String startDateStr = (String) payload.get("startDate");
        String endDateStr = (String) payload.get("endDate");
        String reason = (String) payload.get("reason");

        WfhRequest request = WfhRequest.builder()
                .user(user)
                .startDate(java.time.LocalDate.parse(startDateStr))
                .endDate(java.time.LocalDate.parse(endDateStr))
                .reason(reason)
                .status("PENDING")
                .createdAt(LocalDateTime.now(INDIA_ZONE))
                .build();
        
        wfhRequestRepository.save(request);
        return ResponseEntity.ok(Map.of("message", "WFH Request submitted successfully"));
    }

    @GetMapping("/my-status")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyStatus(@AuthenticationPrincipal UserDetailsImpl principal) {
        return wfhRequestRepository.findPendingByUserId(principal.getId())
                .map(req -> ResponseEntity.ok(Map.of(
                        "wfhStatus", req.getStatus(),
                        "startDate", req.getStartDate(),
                        "endDate", req.getEndDate(),
                        "createdAt", req.getCreatedAt()
                )))
                .orElse(ResponseEntity.ok(Map.of("wfhStatus", "NONE")));
    }

    @GetMapping("/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getRequests(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        
        User currentUser = userRepository.findById(principal.getId()).orElseThrow();
        String role = currentUser.getRole().getName();

        List<WfhRequest> allRequests = wfhRequestRepository.findAll();
        
        List<Map<String, Object>> response = allRequests.stream()
                .filter(req -> status == null || "ALL".equalsIgnoreCase(status) || req.getStatus().equalsIgnoreCase(status))
                .filter(req -> {
                    // ADMIN sees everything
                    if ("ADMIN".equals(role)) return true;
                    
                    // MANAGER sees their subordinates
                    if ("MANAGER".equals(role)) {
                        return req.getUser().getManager() != null && req.getUser().getManager().getId().equals(currentUser.getId());
                    }
                    
                    // TL sees their squad
                    if ("TEAM_LEADER".equals(role) || "TL".equals(role)) {
                        return req.getUser().getSupervisor() != null && req.getUser().getSupervisor().getId().equals(currentUser.getId());
                    }
                    
                    return false;
                })
                .map(req -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", req.getId());
                    map.put("userName", req.getUser().getName());
                    map.put("userRole", req.getUser().getRole().getName());
                    map.put("startDate", req.getStartDate());
                    map.put("endDate", req.getEndDate());
                    map.put("reason", req.getReason());
                    map.put("status", req.getStatus());
                    map.put("adminNotes", req.getAdminNotes());
                    map.put("createdAt", req.getCreatedAt());
                    map.put("respondedAt", req.getRespondedAt());
                    return map;
                }).collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/request/{id}")
    @Transactional
    public ResponseEntity<?> handleRequest(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        
        WfhRequest request = wfhRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        
        User responder = userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("Responder not found"));

        String action = (String) payload.get("action"); // APPROVED or REJECTED
        String notes = (String) payload.get("notes");

        request.setStatus(action);
        request.setAdminNotes(notes);
        request.setRespondedAt(LocalDateTime.now(INDIA_ZONE));
        request.setRespondedBy(responder);
        
        wfhRequestRepository.save(request);
        return ResponseEntity.ok(Map.of("message", "Request processed successfully"));
    }
    @GetMapping("/pending-count")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> getPendingCount(@AuthenticationPrincipal UserDetailsImpl principal) {
        User currentUser = userRepository.findById(principal.getId()).orElseThrow();
        String role = currentUser.getRole().getName();

        long count = wfhRequestRepository.findAll().stream()
                .filter(req -> "PENDING".equalsIgnoreCase(req.getStatus()))
                .filter(req -> {
                    if ("ADMIN".equals(role)) return true;
                    if ("MANAGER".equals(role)) {
                        return req.getUser().getManager() != null && req.getUser().getManager().getId().equals(currentUser.getId());
                    }
                    if ("TEAM_LEADER".equals(role) || "TL".equals(role)) {
                        return req.getUser().getSupervisor() != null && req.getUser().getSupervisor().getId().equals(currentUser.getId());
                    }
                    return false;
                }).count();

        return ResponseEntity.ok(Map.of("count", count));
    }
}
