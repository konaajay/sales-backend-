package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.AttendancePolicyDTO;
import com.lms.www.leadmanagement.dto.OfficeLocationDTO;
import com.lms.www.leadmanagement.entity.AttendancePolicy;
import com.lms.www.leadmanagement.entity.AttendanceShift;
import com.lms.www.leadmanagement.entity.GlobalTarget;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import com.lms.www.leadmanagement.service.AttendancePolicyService;
import com.lms.www.leadmanagement.service.AttendanceService;
import com.lms.www.leadmanagement.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEAM_LEADER')")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;
    private final AttendancePolicyService attendancePolicyService;
    private final SecurityService securityService;

    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getSummaries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long userId) {
        Long requesterId = securityService.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getDailySummaries(startDate, endDate, userId, requesterId)));
    }

    // Office/Branch Management
    @GetMapping("/offices")
    public ResponseEntity<ApiResponse<List<OfficeLocationDTO>>> getOffices() {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.getAllOffices()));
    }

    @PostMapping("/offices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OfficeLocation>> createOffice(@RequestBody OfficeLocation office) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.createOffice(office)));
    }

    @PutMapping("/offices/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OfficeLocation>> updateOffice(@PathVariable Long id, @RequestBody OfficeLocation office) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.updateOffice(id, office)));
    }

    @DeleteMapping("/offices/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOffice(@PathVariable Long id) {
        attendancePolicyService.deleteOffice(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Policy Management
    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<AttendancePolicyDTO>>> getPolicies() {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.getAllPolicies()));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttendancePolicy>> createPolicy(@RequestBody AttendancePolicyDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.createPolicy(dto)));
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttendancePolicy>> updatePolicy(@PathVariable Long id, @RequestBody AttendancePolicyDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.updatePolicy(id, dto)));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable Long id) {
        attendancePolicyService.deletePolicy(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Shift Management
    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<List<AttendanceShift>>> getShifts() {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.getAllShifts()));
    }

    @PostMapping("/shifts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttendanceShift>> createShift(@RequestBody AttendanceShift shift) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.createShift(shift)));
    }

    @PutMapping("/shifts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AttendanceShift>> updateShift(@PathVariable Long id, @RequestBody AttendanceShift shift) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.updateShift(id, shift)));
    }

    @DeleteMapping("/shifts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteShift(@PathVariable Long id) {
        attendancePolicyService.deleteShift(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/force-clock-out/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AttendanceDTO>> forceClockOut(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.clockOut(userId)));
    }

    // Global Targets
    @GetMapping("/global-targets")
    public ResponseEntity<ApiResponse<GlobalTarget>> getGlobalTargets() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getGlobalTarget()));
    }

    @PostMapping("/global-targets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GlobalTarget>> updateGlobalTargets(@RequestBody GlobalTarget target) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.updateGlobalTarget(target)));
    }
}
