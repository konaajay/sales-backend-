package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.LeadTaskDTO;
import com.lms.www.leadmanagement.dto.CreateTaskRequest;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.LeadTask;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.LeadTaskRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class LeadTaskController {

    @Autowired
    private LeadTaskRepository leadTaskRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<LeadTaskDTO>> getAllHierarchicalTasks() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        com.lms.www.leadmanagement.entity.User requester = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        
        String role = requester.getRole().getName();
        List<LeadTask> tasks;

        if ("ADMIN".equals(role)) {
            tasks = leadTaskRepository.findAll();
        } else {
            java.util.List<com.lms.www.leadmanagement.entity.User> visibleUsers = new java.util.ArrayList<>();
            visibleUsers.add(requester);
            
            // Mandatory hierarchy for Manager/TL
            List<Long> subIds = userRepository.findSubordinateIds(requester.getId());
            if (subIds != null && !subIds.isEmpty()) {
                visibleUsers.addAll(userRepository.findAllById(subIds));
            }
            
            // 1. Self-Healing Repair Engine: Scan for orphaned lead dates
            List<Lead> leadsWithDates = leadRepository.findByAssignedToIn(visibleUsers).stream()
                .filter(l -> l.getFollowUpDate() != null)
                .filter(l -> !List.of(Lead.Status.LOST, Lead.Status.PAID, Lead.Status.CONVERTED).contains(l.getStatus()))
                .collect(Collectors.toList());
                
            for (Lead l : leadsWithDates) {
                if (!leadTaskRepository.existsByLeadIdAndStatusAndDueDate(l.getId(), LeadTask.TaskStatus.PENDING, l.getFollowUpDate())) {
                    String type = Lead.Status.EMI.equals(l.getStatus()) ? "EMI_COLLECTION" : "FOLLOW_UP";
                    LeadTask repair = LeadTask.builder()
                            .lead(l)
                            .title((Lead.Status.EMI.equals(l.getStatus()) ? "EMI Collection: " : "Follow-up Call: ") + l.getName())
                            .description("Auto-repaired task from lead due-date synchronization.")
                            .dueDate(l.getFollowUpDate())
                            .status(LeadTask.TaskStatus.PENDING)
                            .taskType(type)
                            .build();
                    leadTaskRepository.save(repair);
                }
            }
            
            tasks = leadTaskRepository.findByLeadAssignedToIn(visibleUsers);
        }
        
        return ResponseEntity.ok(tasks.stream().map(this::convertToDTO).collect(Collectors.toList()));
    }

    @GetMapping("/lead/{leadId}")
    public ResponseEntity<List<LeadTaskDTO>> getTasksByLead(@PathVariable Long leadId) {
        return ResponseEntity.ok(leadTaskRepository.findByLeadId(leadId).stream().map(this::convertToDTO).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<LeadTaskDTO>> searchTasksByDate(@RequestParam Long leadId, @RequestParam String date) {
        try {
            LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");
            return ResponseEntity.ok(leadTaskRepository.findByLeadIdAndDueDateBetween(leadId, start, end).stream().map(this::convertToDTO).collect(Collectors.toList()));
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/lead/{leadId}")
    public ResponseEntity<LeadTaskDTO> addTask(@PathVariable Long leadId, @RequestBody LeadTask task) {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new RuntimeException("Lead not found"));
        task.setLead(lead);
        return ResponseEntity.ok(convertToDTO(leadTaskRepository.save(task)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VIEW_LEADS') or hasAuthority('ADMIN')")
    public ResponseEntity<LeadTaskDTO> createManualTask(@RequestBody CreateTaskRequest request) {
        Long leadId = request.getLeadId();
        Lead lead = null;
        if (leadId != null) {
            lead = leadRepository.findById(leadId).orElseThrow(() -> new RuntimeException("Lead not found"));
        }

        LocalDateTime dueDate;
        try {
            dueDate = LocalDateTime.parse(request.getDueDate());
        } catch (DateTimeParseException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }

        LeadTask task = LeadTask.builder()
                .lead(lead)
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(dueDate)
                .status(LeadTask.TaskStatus.PENDING)
                .taskType(request.getTaskType() != null ? request.getTaskType() : "MANUAL")
                .build();

        LeadTask saved = leadTaskRepository.save(task);
        return ResponseEntity.ok(convertToDTO(saved));
    }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<LeadTaskDTO> updateStatus(@PathVariable Long taskId, @RequestParam LeadTask.TaskStatus status) {
        LeadTask task = leadTaskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        task.setStatus(status);
        return ResponseEntity.ok(convertToDTO(leadTaskRepository.save(task)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        leadTaskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private LeadTaskDTO convertToDTO(LeadTask task) {
        return LeadTaskDTO.builder()
                .id(task.getId())
                .lead(task.getLead() != null ? com.lms.www.leadmanagement.dto.LeadDTO.fromEntity(task.getLead()) : null)
                .leadId(task.getLead() != null ? task.getLead().getId() : null)
                .leadName(task.getLead() != null ? task.getLead().getName() : "General Task")
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .status(task.getStatus() != null ? task.getStatus().name() : "PENDING")
                .taskType(task.getTaskType())
                .createdAt(task.getCreatedAt())
                .build();
    }
}
