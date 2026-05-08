package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.LeadTaskDTO;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.LeadTask;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.LeadTaskRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadTaskService {

    private final LeadTaskRepository leadTaskRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Transactional(readOnly = true)
    public List<LeadTaskDTO> getHierarchicalTasks(String from, String to, Long managerId, Long userId, Long teamId) {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> allowedUserIds = securityService.getAllowedUserIds(requester);
        boolean isGlobalAdmin = securityService.isAdmin(requester) && managerId == null && userId == null && teamId == null;

        LocalDateTime start = null;
        LocalDateTime end = null;
        try {
            if (from != null) start = java.time.LocalDate.parse(from).atStartOfDay();
            if (to != null) end = java.time.LocalDate.parse(to).atTime(java.time.LocalTime.MAX);
        } catch (Exception e) {
            log.warn("Invalid date format in task filter: from={}, to={}", from, to);
        }

        java.util.Set<Long> targetIds = new java.util.HashSet<>();
        if (userId != null) {
            securityService.validateAccess(requester, userId);
            targetIds.add(userId);
        } else if (teamId != null) {
            securityService.validateAccess(requester, teamId);
            targetIds.add(teamId);
            targetIds.addAll(userRepository.findSubordinateIds(teamId));
        } else if (managerId != null) {
            securityService.validateAccess(requester, managerId);
            targetIds.add(managerId);
            targetIds.addAll(userRepository.findSubordinateIds(managerId));
        } else {
            targetIds = allowedUserIds;
        }

        List<LeadTask> tasks;
        if (isGlobalAdmin && from == null && to == null) {
            tasks = leadTaskRepository.findAll();
        } else {
            tasks = leadTaskRepository.findFilteredByUserIds(new java.util.ArrayList<>(targetIds), start, end);
        }

        return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeadTaskDTO> getTasksByLead(Long leadId) {
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new RuntimeException("Lead not found"));
        User requester = securityService.getCurrentUser();
        
        if (lead.getAssignedTo() != null) {
            securityService.validateAccess(requester, lead.getAssignedTo().getId());
        } else if (lead.getCreatedBy() != null) {
            securityService.validateAccess(requester, lead.getCreatedBy().getId());
        }

        return leadTaskRepository.findByLeadId(leadId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadTaskDTO createTask(Long leadId, LeadTask task) {
        User requester = securityService.getCurrentUser();
        Lead lead = leadId != null ? leadRepository.findById(leadId).orElseThrow() : null;

        if (lead != null) {
            if (lead.getAssignedTo() != null) {
                securityService.validateAccess(requester, lead.getAssignedTo().getId());
            }
            task.setLead(lead);
            if (task.getAssignedTo() == null) task.setAssignedTo(lead.getAssignedTo());
        }

        if (task.getAssignedTo() == null) task.setAssignedTo(requester);
        else securityService.validateAccess(requester, task.getAssignedTo().getId());

        task.setCreatedBy(requester);
        task.setStatus(LeadTask.TaskStatus.PENDING);
        
        return convertToDTO(leadTaskRepository.save(task));
    }

    @Transactional
    public LeadTaskDTO updateStatus(Long taskId, LeadTask.TaskStatus status) {
        LeadTask task = leadTaskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        User requester = securityService.getCurrentUser();

        if (task.getAssignedTo() != null) {
            securityService.validateAccess(requester, task.getAssignedTo().getId());
        }

        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        return convertToDTO(leadTaskRepository.save(task));
    }

    private LeadTaskDTO convertToDTO(LeadTask task) {
        return LeadTaskDTO.builder()
                .id(task.getId())
                .lead(task.getLead() != null ? com.lms.www.leadmanagement.dto.LeadDTO.fromEntity(task.getLead()) : null)
                .leadId(task.getLead() != null ? task.getLead().getId() : null)
                .leadName(task.getLead() != null ? task.getLead().getName() : "General Task")
                .assignedToId(task.getAssignedTo() != null ? task.getAssignedTo().getId() : null)
                .assignedToName(task.getAssignedTo() != null ? task.getAssignedTo().getName() : null)
                .createdById(task.getCreatedBy() != null ? task.getCreatedBy().getId() : null)
                .createdByName(task.getCreatedBy() != null ? task.getCreatedBy().getName() : "System")
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .status(task.getStatus() != null ? task.getStatus().name() : "PENDING")
                .taskType(task.getTaskType())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
    @Transactional
    public void deleteTask(Long id) {
        leadTaskRepository.deleteById(id);
    }
}
