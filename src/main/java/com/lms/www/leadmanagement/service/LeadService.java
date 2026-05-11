package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.DashboardProjection;
import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.exception.InvalidRequestException;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.www.leadmanagement.dto.StatusUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CallRecordRepository callRecordRepository;
    private final LeadNoteRepository leadNoteRepository;
    private final LeadTaskRepository leadTaskRepository;
    private final PaymentRepository paymentRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final SecurityService securityService;
    private final MailService mailService;
    private final LeadAuditLogRepository leadAuditLogRepository;
    private final CallLogService callLogService;

    @Transactional(readOnly = true)
    public Map<String, Object> getLeadStats() {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> targetIds = securityService.getAllowedUserIds(requester);

        LocalDateTime start = LocalDateTime.now().minusYears(1); // Or dynamic
        LocalDateTime end = LocalDateTime.now();

        // High performance aggregation via Projection
        List<DashboardProjection> projections = leadRepository.countByStatusForUsers(targetIds, start, end);

        Map<String, Object> stats = new HashMap<>();
        // Dynamic Bucket Discovery
        List<String> successStatuses = pipelineStageRepository
                .findByAnalyticBucketIn(List.of("SUCCESS", "CONVERTED", "PAID"))
                .stream().map(s -> s.getStatusValue().toUpperCase()).collect(Collectors.toList());
        if (successStatuses.isEmpty())
            successStatuses = List.of("CONVERTED", "PAID", "EMI", "SUCCESS");

        List<String> lostStatuses = pipelineStageRepository
                .findByAnalyticBucketIn(List.of("LOST", "NOT_INTERESTED", "REJECTED"))
                .stream().map(s -> s.getStatusValue().toUpperCase()).collect(Collectors.toList());
        if (lostStatuses.isEmpty())
            lostStatuses = List.of("LOST", "NOT_INTERESTED", "REJECTED");

        long total = 0;
        long converted = 0;
        long lost = 0;

        for (DashboardProjection p : projections) {
            String status = p.getStatus() != null ? p.getStatus().toUpperCase() : "";
            long count = p.getCount();
            total += count;

            if (successStatuses.contains(status))
                converted += count;
            else if (lostStatuses.contains(status))
                lost += count;
        }

        stats.put("total", total);
        stats.put("convertedCount", converted);
        stats.put("lostCount", lost);

        // Revenue (Optimized sum)
        stats.put("totalRevenue", paymentRepository.getTotalRevenueIn(targetIds, start, end));

        return stats;
    }

    @Transactional(readOnly = true)
    public List<LeadDTO> getMyLeads() {
        User user = securityService.getCurrentUser();
        List<User> context = List.of(user);

        return leadRepository.findListByAssignedToInOrCreatedByIn(context, context)
                .stream()
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(l -> convertToDTO(l))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeadDTO> getLeadsForTeamLeader(LocalDateTime start, LocalDateTime end, Long targetUserId) {
        User tl = securityService.getCurrentUser();
        java.util.Set<Long> targetIds = securityService.getAllowedUserIds(tl);

        if (targetUserId != null) {
            securityService.validateAccess(tl, targetUserId);
            targetIds = java.util.Collections.singleton(targetUserId);
        }

        List<User> targetUsers = userRepository.findAllById(targetIds);
        return leadRepository.findListByAssignedToInOrCreatedByIn(targetUsers, targetUsers).stream()
                .filter(l -> (start == null || !l.getCreatedAt().isBefore(start))
                        && (end == null || !l.getCreatedAt().isAfter(end)))
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(l -> convertToDTO(l))
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO createLead(LeadDTO dto) {
        if (dto.getMobile() == null || dto.getMobile().isEmpty())
            throw new InvalidRequestException("Mobile required");
        if (leadRepository.existsByMobile(dto.getMobile()))
            throw new InvalidRequestException("Lead already exists");

        User creator = securityService.getCurrentUser();
        
        // AUTO-ASSIGNMENT PROTOCOL: If an Associate adds a lead, they OWN it immediately.
        User assignedTo = null;
        String status = "NEW";
        
        if (creator.getRole().getName().equalsIgnoreCase("ASSOCIATE")) {
            assignedTo = creator;
            status = "WORKING"; // Associates start in WORKING mode
        }

        Lead lead = Lead.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .mobile(dto.getMobile())
                .college(dto.getCollege())
                .status(status)
                .course(dto.getCourseId() != null ? courseRepository.findById(dto.getCourseId()).orElse(null) : null)
                .createdBy(creator)
                .assignedTo(assignedTo)
                .build();

        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updateStatus(Long id, StatusUpdateRequest request) {
        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        User user = securityService.getCurrentUser();

        // Security check
        if (!securityService.isAdmin(user) && !securityService.isManager(user)) {
            if (lead.getAssignedTo() != null) {
                securityService.validateAccess(user, lead.getAssignedTo().getId());
            } else if (lead.getCreatedBy() != null) {
                securityService.validateAccess(user, lead.getCreatedBy().getId());
            }
        }
        String currentStatus = lead.getStatus() != null ? lead.getStatus() : "NEW";
        if (List.of("PAID", "SUCCESS", "EMI", "CONVERTED").contains(currentStatus.toUpperCase())) {
            throw new InvalidRequestException("Cannot change status of a finalized lead");
        }

        if (request.getCourseId() != null) {
            courseRepository.findById(request.getCourseId()).ifPresent(lead::setCourse);
        }

        String status = request.getStatus();
        if (status == null) status = "NEW";
        
        if ("CONVERTED".equalsIgnoreCase(status)) {
            throw new InvalidRequestException("Manual conversion restricted. Leads can only be marked as CONVERTED via successful payment processing.");
        }

        if (!currentStatus.equalsIgnoreCase(status)) {
            StringBuilder sb = new StringBuilder(status);
            if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
                sb.append("\n\nNote: ").append(request.getNote());
            }
            if (request.getDueDate() != null && !request.getDueDate().trim().isEmpty()) {
                sb.append("\nFollow-up: ").append(request.getDueDate().replace("T", " "));
            }
            recordAuditLog(lead.getId(), user, "STATUS", currentStatus, sb.toString(), "STATUS_CHANGE");
        }

        lead.setStatus(status);
        lead.setUpdatedBy(user);

        if ("CONVERTED".equalsIgnoreCase(status)) {
            initializeStudentFee(lead, request);
        }

        saveNote(lead, user, request.getNote(), status, true);
        triggerPipelineActions(lead, status, request);
        
        return convertToDTO(leadRepository.save(lead));
    }

    private void initializeStudentFee(Lead lead, StatusUpdateRequest request) {
        BigDecimal total = request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        BigDecimal netTotal = total.subtract(discount);
        BigDecimal paid = request.getPaidAmount() != null ? request.getPaidAmount() : BigDecimal.ZERO;

        LocalDateTime firstDue = null;
        if (request.getNextInstallmentDate() != null && !request.getNextInstallmentDate().isEmpty()) {
            try {
                firstDue = LocalDateTime
                        .parse(request.getNextInstallmentDate().contains("T") ? request.getNextInstallmentDate()
                                : request.getNextInstallmentDate() + "T10:00:00");
            } catch (Exception e) {
                log.warn("Invalid installment date format: {}", request.getNextInstallmentDate());
            }
        }

        StudentFee fee = studentFeeRepository.findByLeadId(lead.getId())
                .orElse(StudentFee.builder()
                        .leadId(lead.getId())
                        .studentName(lead.getName())
                        .studentEmail(lead.getEmail())
                        .studentMobile(lead.getMobile())
                        .paidAmount(BigDecimal.ZERO)
                        .build());

        fee.setTotalAmount(total);
        fee.setDiscount(discount);
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            fee.setPaidAmount(fee.getPaidAmount().add(paid));
        }
        fee.setBalanceAmount(netTotal.subtract(fee.getPaidAmount()));

        // Handle full installment map if provided
        if (request.getInstallments() != null && !request.getInstallments().isEmpty()) {
            // CRITICAL: Clear old pending installments before creating new roadmap
            paymentRepository.deleteByLeadIdAndStatusAndPaymentTypeIn(
                lead.getId(), 
                Payment.Status.PENDING, 
                List.of("EMI_INSTALLMENT", "INSTALLMENT")
            );

            for (StatusUpdateRequest.InstallmentMap inst : request.getInstallments()) {
                LocalDateTime due = null;
                try {
                    due = LocalDateTime.parse(
                            inst.getDueDate().contains("T") ? inst.getDueDate() : inst.getDueDate() + "T10:00:00");
                } catch (Exception e) {
                }

                if (due != null && inst.getAmount() != null) {
                    paymentRepository.save(Payment.builder()
                            .leadId(lead.getId())
                            .amount(inst.getAmount())
                            .status(Payment.Status.PENDING)
                            .paymentMethod(request.getPaymentMethod())
                            .paymentType("EMI_INSTALLMENT")
                            .dueDate(due)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build());
                    if (fee.getNextDueDate() == null || due.isBefore(fee.getNextDueDate())) {
                        fee.setNextDueDate(due);
                    }
                }
            }
        } else if (firstDue != null) {
            fee.setNextDueDate(firstDue);
        }

        studentFeeRepository.save(fee);

        // If there's an initial payment, record it
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            paymentRepository.save(Payment.builder()
                    .leadId(lead.getId())
                    .amount(paid)
                    .status(Payment.Status.PAID)
                    .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "MANUAL")
                    .paymentGatewayId("INITIAL_DEPOSIT_" + System.currentTimeMillis())
                    .date(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        if ("EMI".equalsIgnoreCase(request.getPaymentType())) {
            lead.setFollowUpType("EMI_COLLECTION");
            lead.setFollowUpDate(fee.getNextDueDate());
            lead.setFollowUpRequired(true);
        } else {
            lead.setFollowUpType("ADMISSION_SUCCESS");
            lead.setFollowUpRequired(false);
        }
    }

    private void saveNote(Lead lead, User creator, String content, String status, boolean skipAudit) {
        boolean hasContent = content != null && !content.trim().isEmpty();
        
        LeadNote note = LeadNote.builder()
                .lead(lead)
                .createdBy(creator)
                .content(hasContent ? content : "Status update: " + status)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        leadNoteRepository.save(note);
        
        if (!skipAudit && hasContent) {
            // Only record in audit log if there's actual content
            recordAuditLog(lead.getId(), creator, "NOTE", "NEW", content, "NOTE_ADDED");
        }
    }

    private void triggerPipelineActions(Lead lead, String status, StatusUpdateRequest request) {
        boolean hasExplicitDueDate = request.getDueDate() != null && !request.getDueDate().isEmpty();
        User requester = securityService.getCurrentUser();

        pipelineStageRepository.findByStatusValue(status).ifPresent(stage -> {
            if (stage.isCreateTask() || hasExplicitDueDate) {
                if (stage.isRequireDate() && !hasExplicitDueDate) {
                    throw new InvalidRequestException("Date required for status: " + status);
                }

                LocalDateTime dueDate = LocalDateTime.now().plusDays(stage.getDefaultFollowupDays());
                if (hasExplicitDueDate) {
                    try {
                        dueDate = LocalDateTime.parse(request.getDueDate().contains("T") ? request.getDueDate()
                                : request.getDueDate() + "T10:00:00");
                    } catch (Exception e) {
                        log.warn("Invalid due date format: {}", request.getDueDate());
                    }
                }

                if (!leadTaskRepository.existsByLeadIdAndStatusAndDueDate(lead.getId(), LeadTask.TaskStatus.PENDING,
                        dueDate)) {
                    leadTaskRepository.save(LeadTask.builder()
                            .lead(lead)
                            .assignedTo(lead.getAssignedTo())
                            .createdBy(requester)
                            .title("Follow-up: " + stage.getLabel())
                            .dueDate(dueDate)
                            .status(LeadTask.TaskStatus.PENDING)
                            .taskType("FOLLOW_UP")
                            .build());
                }
            }
        });

        // Fallback for custom statuses not in pipeline_stage table but having explicit
        // due date
        if (hasExplicitDueDate && pipelineStageRepository.findByStatusValue(status).isEmpty()) {
            try {
                LocalDateTime dueDate = LocalDateTime.parse(
                        request.getDueDate().contains("T") ? request.getDueDate() : request.getDueDate() + "T10:00:00");
                if (!leadTaskRepository.existsByLeadIdAndStatusAndDueDate(lead.getId(), LeadTask.TaskStatus.PENDING,
                        dueDate)) {
                    leadTaskRepository.save(LeadTask.builder()
                            .lead(lead)
                            .assignedTo(lead.getAssignedTo())
                            .createdBy(requester)
                            .title("Follow-up: " + status)
                            .dueDate(dueDate)
                            .status(LeadTask.TaskStatus.PENDING)
                            .taskType("FOLLOW_UP")
                            .build());
                }
            } catch (Exception e) {
            }
        }

        if ("LOST".equalsIgnoreCase(status) || "NOT_INTERESTED".equalsIgnoreCase(status)) {
            leadTaskRepository.cancelAllPendingByLeadId(lead.getId());
        }
    }

    @Transactional
    public LeadDTO assignLead(Long leadId, Long userId) {
        Lead lead = leadRepository.findById(leadId).orElseThrow();
        User requester = securityService.getCurrentUser();

        // Security check for the lead itself
        if (lead.getAssignedTo() != null) {
            securityService.validateAccess(requester, lead.getAssignedTo().getId());
        } else if (lead.getCreatedBy() != null) {
            securityService.validateAccess(requester, lead.getCreatedBy().getId());
        }

        if (userId == null || userId == 0) {
            // UNASSIGNING PATH
            String oldVal = lead.getAssignedTo() != null ? lead.getAssignedTo().getName() : "UNASSIGNED";
            
            if (lead.getAssignedTo() != null && securityService.isTeamLeader(requester)) {
                // Count every change action
                int currentCount = lead.getReassignmentCount() != null ? lead.getReassignmentCount() : 0;
                if (currentCount >= 2) {
                    throw new InvalidRequestException("Action blocked: This lead has reached its maximum transfer limit (1 assignment + 1 reassignment).");
                }
                lead.setReassignmentCount(currentCount + 1);
            }

            lead.setAssignedTo(null);
            recordAuditLog(lead.getId(), requester, "ASSIGNMENT", oldVal, "UNASSIGNED", "ASSIGNMENT_CHANGE");
        } else {
            // ASSIGNING PATH
            User target = userRepository.findById(userId).orElseThrow();
            securityService.validateHierarchyAccess(requester, target);
            String oldVal = lead.getAssignedTo() != null ? lead.getAssignedTo().getName() : "UNASSIGNED";

            // Any change in assignment status counts as a move
            boolean isChanging = (lead.getAssignedTo() == null) || !lead.getAssignedTo().getId().equals(target.getId());
            boolean isTL = securityService.isTeamLeader(requester) || "teamlead1@lms.com".equalsIgnoreCase(requester.getEmail());
            
            log.info("[ASSIGN] User {} (isTL: {}) attempting to assign Lead #{} to User {}. currentCount: {}", 
                requester.getEmail(), isTL, leadId, userId, lead.getReassignmentCount());

            if (isChanging && isTL && !"NEW".equalsIgnoreCase(lead.getStatus())) {
                int currentCount = lead.getReassignmentCount() != null ? lead.getReassignmentCount() : 0;
                log.info("[COUNT-CHECK] Lead #{} current actions: {}", leadId, currentCount);
                if (currentCount >= 2) {
                    log.warn("[BLOCK] Reassignment limit reached for Lead #{} by TL {}", leadId, requester.getEmail());
                    throw new InvalidRequestException("Strategic restriction: Team Leaders can only assign and reassign a lead once to maintain follow-up stability.");
                }
                lead.setReassignmentCount(currentCount + 1);
                log.info("[COUNT-SUCCESS] Lead #{} updated count to {}", leadId, lead.getReassignmentCount());
            }

            lead.setAssignedTo(target);
            recordAuditLog(lead.getId(), requester, "ASSIGNMENT", oldVal, target.getName(), "ASSIGNMENT_CHANGE");
            // Reset status to NEW upon assignment unless it's already finalized
            String currentStatus = lead.getStatus() != null ? lead.getStatus() : "NEW";
            if (!List.of("PAID", "SUCCESS", "EMI", "CONVERTED").contains(currentStatus.toUpperCase())) {
                lead.setStatus("NEW");
            }
        }
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public List<LeadDTO> bulkAssignLeads(List<Long> leadIds, Long userId) {
        User target = null;
        if (userId != null && userId != 0) {
            target = userRepository.findById(userId).orElseThrow();
            securityService.validateHierarchyAccess(securityService.getCurrentUser(), target);
        }

        List<Lead> leads = leadRepository.findAllById(leadIds);
        User requester = securityService.getCurrentUser();
        User finalTarget = target;
        
        leads.forEach(l -> {
            // Check if this action constitutes a reassignment change
            boolean isChanging = (finalTarget == null && l.getAssignedTo() != null) || 
                               (finalTarget != null && (l.getAssignedTo() == null || !l.getAssignedTo().getId().equals(finalTarget.getId())));

            if (isChanging && securityService.isTeamLeader(requester) && !"NEW".equalsIgnoreCase(l.getStatus())) {
                int currentCount = l.getReassignmentCount() != null ? l.getReassignmentCount() : 0;
                if (currentCount >= 2) {
                    throw new InvalidRequestException("Bulk action failed for Lead #" + l.getId() + ": Maximum transfer limit reached (1 assignment + 1 reassignment).");
                }
                l.setReassignmentCount(currentCount + 1);
            }

            l.setAssignedTo(finalTarget);
            // Reset status to NEW upon bulk assignment unless it's already finalized
            String currentStatus = l.getStatus() != null ? l.getStatus() : "NEW";
            if (!List.of("PAID", "SUCCESS", "EMI", "CONVERTED").contains(currentStatus.toUpperCase())) {
                l.setStatus("NEW");
            }
        });
        return leadRepository.saveAll(leads).stream().map(l -> convertToDTO(l)).collect(Collectors.toList());
    }

    private LeadDTO convertToDTO(Lead lead) {
        LeadDTO dto = LeadDTO.fromEntity(lead);
        
        // Fetch granular payment progress if it exists
        studentFeeRepository.findByLeadId(lead.getId()).ifPresent(fee -> {
            dto.setPaymentStatus(fee.getPaymentStatus());
            dto.setTotalAmount(fee.getTotalAmount());
            dto.setPaidAmount(fee.getPaidAmount());
            dto.setBalanceAmount(fee.getBalanceAmount());
            dto.setDiscount(fee.getDiscount());
            dto.setTotalInstallments(fee.getTotalInstallments());
            dto.setPaidInstallments(fee.getPaidInstallments());
            dto.setNextPaymentDueDate(fee.getNextDueDate());
        });

        return dto;
    }

    @Transactional(readOnly = true)
    public LeadDTO getLeadById(Long id) {
        return leadRepository.findById(id).map(this::convertToDTO).orElseThrow();
    }

    public User getCurrentUser() {
        return securityService.getCurrentUser();
    }

    @Transactional(readOnly = true)
    public List<LeadDTO> getAllLeadsForManager(Long managerId, Long userId) {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> targetIds = securityService.getAllowedUserIds(requester);

        if (managerId != null) {
            securityService.validateAccess(requester, managerId);
            targetIds = new java.util.HashSet<>(userRepository.findSubordinateIds(managerId));
            targetIds.add(managerId);
        } else if (userId != null) {
            securityService.validateAccess(requester, userId);
            targetIds = java.util.Collections.singleton(userId);
        }

        List<User> targetUsers = userRepository.findAllById(targetIds);
        return leadRepository.findListByAssignedToInOrCreatedByIn(targetUsers, targetUsers).stream()
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(l -> convertToDTO(l))
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO dto) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        User requester = securityService.getCurrentUser();

        // Security check
        if (lead.getAssignedTo() != null) {
            securityService.validateAccess(requester, lead.getAssignedTo().getId());
        } else if (lead.getCreatedBy() != null) {
            securityService.validateAccess(requester, lead.getCreatedBy().getId());
        }

        // Record field-level changes
        if (dto.getName() != null && !dto.getName().equals(lead.getName())) {
            recordAuditLog(lead.getId(), requester, "NAME", lead.getName(), dto.getName(), "UPDATE");
            lead.setName(dto.getName());
        }
        if (dto.getEmail() != null && !dto.getEmail().equals(lead.getEmail())) {
            recordAuditLog(lead.getId(), requester, "EMAIL", lead.getEmail(), dto.getEmail(), "UPDATE");
            lead.setEmail(dto.getEmail());
        }
        if (dto.getMobile() != null && !dto.getMobile().equals(lead.getMobile())) {
            recordAuditLog(lead.getId(), requester, "MOBILE", lead.getMobile(), dto.getMobile(), "UPDATE");
            lead.setMobile(dto.getMobile());
        }
        if (dto.getCollege() != null && !dto.getCollege().equals(lead.getCollege())) {
            recordAuditLog(lead.getId(), requester, "COLLEGE", lead.getCollege(), dto.getCollege(), "UPDATE");
            lead.setCollege(dto.getCollege());
        }
        if (dto.getCourseId() != null && (lead.getCourse() == null || !dto.getCourseId().equals(lead.getCourse().getId()))) {
            Course newCourse = courseRepository.findById(dto.getCourseId()).orElse(null);
            String oldVal = lead.getCourse() != null ? lead.getCourse().getName() : "NONE";
            String newVal = newCourse != null ? newCourse.getName() : "NONE";
            recordAuditLog(lead.getId(), requester, "COURSE", oldVal, newVal, "UPDATE");
            lead.setCourse(newCourse);
        }

        return convertToDTO(leadRepository.save(lead));
    }

    private void recordAuditLog(Long leadId, User user, String field, String oldVal, String newVal, String action) {
        LeadAuditLog log = LeadAuditLog.builder()
                .leadId(leadId)
                .changedBy(user)
                .fieldName(field)
                .oldValue(oldVal)
                .newValue(newVal)
                .action(action)
                .build();
        leadAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<com.lms.www.leadmanagement.dto.LeadAuditLogDTO> getLeadHistory(Long leadId) {
        return leadAuditLogRepository.findByLeadIdOrderByTimestampDesc(leadId).stream()
                .map(l -> com.lms.www.leadmanagement.dto.LeadAuditLogDTO.builder()
                        .id(l.getId())
                        .fieldName(l.getFieldName())
                        .oldValue(l.getOldValue())
                        .newValue(l.getNewValue())
                        .action(l.getAction())
                        .changedByName(l.getChangedBy() != null ? l.getChangedBy().getName() : "SYSTEM")
                        .timestamp(l.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO rejectLead(Long id, Map<String, Object> data) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        lead.setStatus("REJECTED");
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updateStatus(Long id, String status, String note) {
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus(status);
        request.setNote(note);
        return updateStatus(id, request);
    }

    @Transactional
    public LeadDTO updateNote(Long id, String note) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        User user = securityService.getCurrentUser();
        saveNote(lead, user, note, lead.getStatus() != null ? lead.getStatus() : "NEW", false);
        return convertToDTO(lead);
    }

    @Transactional(readOnly = true)
    public Page<LeadDTO> getAllLeadsFiltered(Long managerId, Long teamId, Long userId, String from, String to,
            Pageable pageable) {
        User requester = securityService.getCurrentUser();

        java.time.LocalDateTime start = null;
        java.time.LocalDateTime end = null;
        try {
            if (from != null && !from.isEmpty())
                start = java.time.LocalDate.parse(from).atStartOfDay();
            if (to != null && !to.isEmpty())
                end = java.time.LocalDate.parse(to).atTime(java.time.LocalTime.MAX);
        } catch (Exception e) {
            log.warn("Invalid date format in lead filter: from={}, to={}", from, to);
        }

        java.util.Set<Long> targetIds = securityService.getAllowedUserIds(requester);
        boolean isUnassigned = false;
        boolean isAdmin = securityService.isAdmin(requester);

        if (userId != null) {
            if (userId == -1L) {
                isUnassigned = true;
            } else {
                if (!isAdmin) securityService.validateAccess(requester, userId);
                targetIds = java.util.Collections.singleton(userId);
            }
        } else if (teamId != null) {
            if (!isAdmin) securityService.validateAccess(requester, teamId);
            targetIds = new java.util.HashSet<>(userRepository.findSubordinateIds(teamId));
            targetIds.add(teamId);
        } else if (managerId != null) {
            if (!isAdmin) securityService.validateAccess(requester, managerId);
            targetIds = new java.util.HashSet<>(userRepository.findSubordinateIds(managerId));
            targetIds.add(managerId);
        }

        return leadRepository.findHierarchicalLeads(targetIds, start, end, isUnassigned, pageable)
                .map(l -> convertToDTO(l));
    }

    @Transactional
    public LeadDTO updatePaymentLink(Long id, String link) {
        return getLeadById(id);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getCurrentUserSubordinates() {
        User user = securityService.getCurrentUser();
        java.util.Set<Long> allowedIds = securityService.getAllowedUserIds(user);
        return userRepository.findAllById(allowedIds).stream()
                .map(UserDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional

    public LeadDTO recordCallOutcome(Long id, Map<String, Object> outcomeData) {
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus((String) outcomeData.get("status"));
        request.setNote((String) outcomeData.get("note"));
        request.setSkipAudit(true);

        if (outcomeData.containsKey("totalAmount")) {
            request.setTotalAmount(new BigDecimal(outcomeData.get("totalAmount").toString()));
        }
        if (outcomeData.containsKey("paidAmount")) {
            request.setPaidAmount(new BigDecimal(outcomeData.get("paidAmount").toString()));
        }
        if (outcomeData.containsKey("paymentMethod")) {
            request.setPaymentMethod((String) outcomeData.get("paymentMethod"));
        }
        if (outcomeData.containsKey("paymentType")) {
            request.setPaymentType((String) outcomeData.get("paymentType"));
        }
        if (outcomeData.containsKey("dueDate")) {
            request.setDueDate((String) outcomeData.get("dueDate"));
        }

        if (outcomeData.containsKey("courseId")) {
            request.setCourseId(Long.valueOf(outcomeData.get("courseId").toString()));
        }

        if (outcomeData.containsKey("discount")) {
            request.setDiscount(new BigDecimal(outcomeData.get("discount").toString()));
        }

        if (outcomeData.containsKey("installments")) {
            List<Map<String, Object>> instList = (List<Map<String, Object>>) outcomeData.get("installments");
            if (instList != null) {
                List<StatusUpdateRequest.InstallmentMap> installments = instList.stream().map(m -> {
                    StatusUpdateRequest.InstallmentMap im = new StatusUpdateRequest.InstallmentMap();
                    im.setAmount(new BigDecimal(m.get("amount").toString()));
                    im.setDueDate((String) m.get("dueDate"));
                    return im;
                }).collect(Collectors.toList());
                request.setInstallments(installments);
            }
        }

        LeadDTO updatedLead = updateStatus(id, request);

        // Record as call log
        Lead lead = leadRepository.findById(id).orElse(null);
        User requester = securityService.getCurrentUser();
        callLogService.recordManualCall(requester, lead, request.getStatus(), request.getNote());
        
        // Also record in audit log for "Full History"
        // Merged into status change above
        
        return updatedLead;
    }

    @Transactional(readOnly = true)
    public List<LeadDTO> getUpcomingFollowUps() {
        User user = securityService.getCurrentUser();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(11); // Buffer to catch the 10m mark
        
        return leadRepository.findUpcomingFollowUps(user.getId(), start, end).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Course> getAllActiveCourses() {
        return courseRepository.findAllByActiveTrue();
    }
}
