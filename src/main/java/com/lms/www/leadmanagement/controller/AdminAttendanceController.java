package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.dto.AttendanceDTO;
import com.lms.www.leadmanagement.dto.AttendancePolicyDTO;
import com.lms.www.leadmanagement.dto.OfficeLocationDTO;
import com.lms.www.leadmanagement.entity.AttendancePolicy;
import com.lms.www.leadmanagement.entity.AttendanceShift;

import com.lms.www.leadmanagement.entity.OfficeLocation;
import com.lms.www.leadmanagement.service.AttendancePolicyService;
import com.lms.www.leadmanagement.service.AttendanceService;
import com.lms.www.leadmanagement.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {
 
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private AttendancePolicyService attendancePolicyService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private com.lms.www.leadmanagement.repository.CourseRepository courseRepository;

    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<List<AttendanceDTO>>> getSummaries(
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getDailySummaries(managerId, teamId, userId, from, to)));
    }

    // Office/Branch Management
    @GetMapping("/offices")
    public ResponseEntity<ApiResponse<List<OfficeLocationDTO>>> getOffices() {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.getAllOffices()));
    }

    @PostMapping("/offices")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<OfficeLocation>> createOffice(@RequestBody OfficeLocation office) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.createOffice(office)));
    }

    @PutMapping("/offices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AttendancePolicy>> createPolicy(@RequestBody AttendancePolicyDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(attendancePolicyService.createPolicy(dto)));
    }

    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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



    @PostMapping("/daily-note")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateDailyNote(@RequestBody java.util.Map<String, Object> body) {
        if (body == null || body.get("userId") == null || body.get("date") == null) {
            return ResponseEntity.badRequest().build();
        }
        Long userId = Long.valueOf(body.get("userId").toString());
        LocalDate date = LocalDate.parse(body.get("date").toString());
        String note = body.get("note") != null ? body.get("note").toString() : "";
        attendanceService.updateDailyNote(userId, date, note);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
     @GetMapping("/courses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<com.lms.www.leadmanagement.entity.Course>>> getCoursesLegacy() {
        return ResponseEntity.ok(ApiResponse.success(courseRepository.findAllByActiveTrue()));
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<com.lms.www.leadmanagement.entity.Course>> createCourseRelocated(@RequestBody com.lms.www.leadmanagement.entity.Course course) {
        if (course.getMinTokenAmount() == null || course.getMinTokenAmount().compareTo(new java.math.BigDecimal("500")) < 0) {
            course.setMinTokenAmount(new java.math.BigDecimal("500"));
        }
        if (course.getMaxInstallments() == null || course.getMaxInstallments() < 1) {
            course.setMaxInstallments(4);
        }
        return ResponseEntity.ok(ApiResponse.success(courseRepository.save(course)));
    }
 
    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<com.lms.www.leadmanagement.entity.Course>> updateCourseRelocated(@PathVariable Long id, @RequestBody com.lms.www.leadmanagement.entity.Course courseDetails) {
        return courseRepository.findById(id).map(course -> {
            course.setName(courseDetails.getName());
            course.setBaseFee(courseDetails.getBaseFee());
            course.setDescription(courseDetails.getDescription());
            course.setActive(courseDetails.isActive());
            if (courseDetails.getMinTokenAmount() != null && courseDetails.getMinTokenAmount().compareTo(new java.math.BigDecimal("500")) >= 0) {
                course.setMinTokenAmount(courseDetails.getMinTokenAmount());
            }
            course.setMaxInstallments(courseDetails.getMaxInstallments() != null ? courseDetails.getMaxInstallments() : 4);
            return ResponseEntity.ok(ApiResponse.success(courseRepository.save(course)));
        }).orElse(ResponseEntity.notFound().build());
    }
 
    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCourseRelocated(@PathVariable Long id) {
        courseRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
