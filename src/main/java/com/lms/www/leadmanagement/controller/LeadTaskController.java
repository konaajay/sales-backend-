package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.LeadTaskDTO;
import com.lms.www.leadmanagement.dto.CreateTaskRequest;
import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.LeadTask;
import com.lms.www.leadmanagement.service.LeadTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class LeadTaskController {

    private final LeadTaskService leadTaskService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeadTaskDTO>>> getAllHierarchicalTasks(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long teamId) {
        
        return ResponseEntity.ok(ApiResponse.success(leadTaskService.getHierarchicalTasks(from, to, managerId, userId, teamId)));
    }

    @GetMapping("/lead/{leadId}")
    public ResponseEntity<ApiResponse<List<LeadTaskDTO>>> getTasksByLead(@PathVariable Long leadId) {
        return ResponseEntity.ok(ApiResponse.success(leadTaskService.getTasksByLead(leadId)));
    }

    @PostMapping("/lead/{leadId}")
    public ResponseEntity<ApiResponse<LeadTaskDTO>> addTask(@PathVariable Long leadId, @RequestBody LeadTask task) {
        return ResponseEntity.ok(ApiResponse.success(leadTaskService.createTask(leadId, task)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VIEW_LEADS') or hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<LeadTaskDTO>> createManualTask(@RequestBody CreateTaskRequest request) {
        LocalDateTime dueDate;
        try {
            dueDate = LocalDateTime.parse(request.getDueDate());
        } catch (DateTimeParseException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }

        LeadTask task = LeadTask.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(dueDate)
                .taskType(request.getTaskType() != null ? request.getTaskType() : "MANUAL")
                .build();

        return ResponseEntity.ok(ApiResponse.success(leadTaskService.createTask(request.getLeadId(), task)));
    }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<ApiResponse<LeadTaskDTO>> updateStatus(@PathVariable Long taskId, @RequestParam LeadTask.TaskStatus status) {
        return ResponseEntity.ok(ApiResponse.success(leadTaskService.updateStatus(taskId, status)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        leadTaskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
