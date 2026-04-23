package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.LeadDTO;
import lombok.extern.slf4j.Slf4j;
import com.lms.www.leadmanagement.entity.CallRecord;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.LeadNote;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.Payment;
import com.lms.www.leadmanagement.entity.LeadTask;
import com.lms.www.leadmanagement.repository.CallRecordRepository;
import com.lms.www.leadmanagement.repository.LeadNoteRepository;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import com.lms.www.leadmanagement.repository.LeadTaskRepository;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LeadService {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CallRecordRepository callRecordRepository;

    @Autowired
    private LeadNoteRepository leadNoteRepository;

    @Autowired
    private LeadPaymentService leadPaymentService;

    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private LeadTaskRepository leadTaskRepository;

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private static final Map<Lead.Status, Integer> STATUS_RANK = new HashMap<>();
    static {
        // Level 0: Fresh
        STATUS_RANK.put(Lead.Status.NEW, 0);
        
        // Level 1: Initial Engagement
        STATUS_RANK.put(Lead.Status.WORKING, 1);
        STATUS_RANK.put(Lead.Status.PENDING_MESSAGES, 1);
        
        // Level 2: Direct Contact
        STATUS_RANK.put(Lead.Status.CONTACTED, 2);
        
        // Level 3: Deep Engagement / Nurturing
        STATUS_RANK.put(Lead.Status.FOLLOW_UP, 3);
        STATUS_RANK.put(Lead.Status.UNDER_REVIEW, 3);
        STATUS_RANK.put(Lead.Status.RETRY, 3);
        STATUS_RANK.put(Lead.Status.PAYMENT_FAILED, 3);
        
        // Level 4: High Intent
        STATUS_RANK.put(Lead.Status.INTERESTED, 4);
        
        // Level 5: Transactional / Successful (Terminal A)
        STATUS_RANK.put(Lead.Status.EMI, 5);
        STATUS_RANK.put(Lead.Status.PAID, 5);
        STATUS_RANK.put(Lead.Status.CONVERTED, 5);
        STATUS_RANK.put(Lead.Status.SUCCESS, 5);
        
        // Level 6: Lost / Closed (Terminal B)
        STATUS_RANK.put(Lead.Status.LOST, 6);
        STATUS_RANK.put(Lead.Status.NOT_INTERESTED, 6);
        STATUS_RANK.put(Lead.Status.CLOSED, 6);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) principal).getId();
        }
        throw new RuntimeException("User not authenticated");
    }

    public Map<String, Object> getLeadStats() {
        Long userId = getCurrentUserId();
        User currentUser = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<Lead> rawLeads = leadRepository.findByAssignedToOrCreatedBy(currentUser, currentUser);
        List<Lead> myLeads = rawLeads.stream()
            .filter(l -> (l.getAssignedTo() != null && l.getAssignedTo().getId().equals(userId)) || 
                         (l.getAssignedTo() == null && l.getCreatedBy() != null && l.getCreatedBy().getId().equals(userId)))
            .collect(Collectors.toList());
        Map<String, Object> stats = new java.util.HashMap<>();

        long total = myLeads.size();
        long convertedCount = myLeads.stream()
                .filter(l -> Lead.Status.PAID.equals(l.getStatus()) || Lead.Status.CONVERTED.equals(l.getStatus())
                        || Lead.Status.EMI.equals(l.getStatus()))
                .count();
        long lostCount = myLeads.stream()
                .filter(l -> Lead.Status.LOST.equals(l.getStatus()) || Lead.Status.NOT_INTERESTED.equals(l.getStatus()))
                .count();

        java.util.List<Long> leadIds = myLeads.stream().map(Lead::getId).collect(Collectors.toList());
        java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
        if (!leadIds.isEmpty()) {
            List<com.lms.www.leadmanagement.entity.Payment> payments = paymentRepository.findByLeadIdIn(leadIds);
            if (payments != null) {
                totalRevenue = payments.stream()
                        .filter(p -> p.getStatus() != null
                                && (com.lms.www.leadmanagement.entity.Payment.Status.PAID.equals(p.getStatus()) ||
                                        com.lms.www.leadmanagement.entity.Payment.Status.SUCCESS.equals(p.getStatus())
                                        ||
                                        com.lms.www.leadmanagement.entity.Payment.Status.APPROVED
                                                .equals(p.getStatus())))
                        .map(com.lms.www.leadmanagement.entity.Payment::getAmount)
                        .filter(java.util.Objects::nonNull)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            }
        }

        stats.put("total", total);
        stats.put("convertedCount", convertedCount);
        stats.put("lostCount", lostCount);
        stats.put("totalRevenue", totalRevenue);

        // Calculate Actionable Stats (Split Follow-ups + Overdue EMIs)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);

        // Bridge the gap: Fetch all pending tasks to ensure dashboard matches the Task Board
        List<LeadTask> allPendingTasks = leadTaskRepository.findFilteredByUserIds(List.of(userId), null, null);
        
        long pendingPayments = 0;
        long pendingFollowUps = 0;
        long followUpPool = 0;
        long todayFollowUps = 0;

        // Optimized Payment Lookup Map
        Map<Long, List<Payment>> leadPaymentsMap = new HashMap<>();
        if (!leadIds.isEmpty()) {
            List<Payment> allPayments = paymentRepository.findByLeadIdIn(leadIds);
            if (allPayments != null) {
                leadPaymentsMap = allPayments.stream().collect(Collectors.groupingBy(Payment::getLeadId));
            }
        }

        for (Lead lead : myLeads) {
            String statusStr = lead.getStatus() != null ? lead.getStatus().name() : "";
            if ("LOST".equals(statusStr)) continue;
            
            // Check Profile-based dates
            boolean profileFollowUpMissed = lead.getFollowUpDate() != null && lead.getFollowUpDate().isBefore(now);
            boolean profileFollowUpToday = lead.getFollowUpDate() != null && !lead.getFollowUpDate().isBefore(startOfDay) && !lead.getFollowUpDate().isAfter(endOfDay);
            boolean isFollowUpStatus = "FOLLOW_UP".equals(statusStr);

            // Check Task-based status for this lead
            boolean taskMissed = allPendingTasks.stream()
                .anyMatch(t -> t.getLead() != null && t.getLead().getId().equals(lead.getId()) && t.getStatus() == LeadTask.TaskStatus.PENDING && t.getDueDate() != null && t.getDueDate().isBefore(now));
            
            boolean taskToday = allPendingTasks.stream()
                .anyMatch(t -> t.getLead() != null && t.getLead().getId().equals(lead.getId()) && t.getStatus() == LeadTask.TaskStatus.PENDING && t.getDueDate() != null && !t.getDueDate().isBefore(startOfDay) && !t.getDueDate().isAfter(endOfDay));

            // Check for overdue payments using the optimized map
            boolean emiOverdue = false;
            List<Payment> leadPayments = leadPaymentsMap.get(lead.getId());
            if (leadPayments != null) {
                emiOverdue = leadPayments.stream()
                    .anyMatch(p -> (p.getStatus() == Payment.Status.PENDING || p.getStatus() == Payment.Status.OVERDUE) 
                        && p.getDueDate() != null && p.getDueDate().isBefore(now));
            }

            if (emiOverdue) pendingPayments++;
            if (profileFollowUpMissed || taskMissed) pendingFollowUps++;
            if (isFollowUpStatus) followUpPool++;
            if (profileFollowUpToday || taskToday) todayFollowUps++;
        }

        // Calculate Revenue Values & Forecast
        BigDecimal pendingPaymentsAmount = BigDecimal.ZERO;
        BigDecimal forecastRevenue = BigDecimal.ZERO;
        LocalDateTime thirtyDaysFromNow = now.plusDays(30);

        if (!leadIds.isEmpty()) {
            List<Payment> allPayments = paymentRepository.findByLeadIdIn(leadIds);
            if (allPayments != null) {
                for (Payment p : allPayments) {
                    if (p.getAmount() == null) continue;
                    
                    // Overdue Amount
                    if ((p.getStatus() == Payment.Status.PENDING || p.getStatus() == Payment.Status.OVERDUE) 
                        && p.getDueDate() != null && p.getDueDate().isBefore(now)) {
                        pendingPaymentsAmount = pendingPaymentsAmount.add(p.getAmount());
                    }
                    
                    // Forecast (Next 30 Days)
                    if (p.getStatus() == Payment.Status.PENDING && p.getDueDate() != null 
                        && !p.getDueDate().isBefore(now) && !p.getDueDate().isAfter(thirtyDaysFromNow)) {
                        forecastRevenue = forecastRevenue.add(p.getAmount());
                    }
                }
            }
        }

        stats.put("pendingPayments", pendingPayments);
        stats.put("pendingPaymentsAmount", pendingPaymentsAmount);
        stats.put("forecastRevenue", forecastRevenue);
        stats.put("pendingFollowUps", pendingFollowUps);
        stats.put("followUpPool", followUpPool);
        stats.put("todayFollowUps", todayFollowUps);
        stats.put("pendingActions", pendingPayments + pendingFollowUps); // For legacy compatibility

        // Backward compatibility
        stats.put("TOTAL", total);
        stats.put("PAID", convertedCount);

        return stats;
    }

    public List<LeadDTO> getMyLeads() {
        Long userId = getCurrentUserId();
        User currentUser = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        List<Lead> rawLeads = leadRepository.findByAssignedToOrCreatedBy(currentUser, currentUser);
        List<Lead> leads = rawLeads.stream()
            .filter(l -> (l.getAssignedTo() != null && l.getAssignedTo().getId().equals(userId)) || 
                         (l.getAssignedTo() == null && l.getCreatedBy() != null && l.getCreatedBy().getId().equals(userId)))
            .collect(Collectors.toList());

        // If the user is ADMIN, also include UNASSIGNED leads so they can be
        // filtered/assigned
        String role = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if ("ADMIN".equals(role)) {
            List<Lead> unassigned = leadRepository.findByAssignedToIsNull();
            leads = new ArrayList<>(leads);
            leads.addAll(unassigned);
        }

        // Always sort by creation date descending
        leads.sort((a, b) -> {
            if (a.getCreatedAt() == null)
                return 1;
            if (b.getCreatedAt() == null)
                return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return leads.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<LeadDTO> getLeadsForTeamLeader(LocalDateTime start, LocalDateTime end, Long targetUserId) {
        User tl = getCurrentUser();
        List<Long> subordinateIds;
        
        if (targetUserId != null) {
            // Validate that the target user is actually a subordinate
            List<Long> actualSubs = userRepository.findSubordinateIds(tl.getId());
            if (targetUserId.equals(tl.getId()) || (actualSubs != null && actualSubs.contains(targetUserId))) {
                subordinateIds = List.of(targetUserId);
            } else {
                throw new RuntimeException("Clearance Denied: Targeted user is outside your direct reporting hierarchy.");
            }
        } else {
            subordinateIds = userRepository.findSubordinateIds(tl.getId());
            subordinateIds.add(tl.getId());
        }

        List<User> subordinates = userRepository.findAllById(subordinateIds);
        List<Lead> leads = leadRepository.findByAssignedToInOrCreatedByIn(subordinates, subordinates);
        
        // Apply Date Filtering in-memory if provided (or use a custom repository method for scale)
        return leads.stream()
                .filter(l -> {
                    if (start == null && end == null) return true;
                    LocalDateTime createdAt = l.getCreatedAt();
                    if (createdAt == null) return false;
                    boolean afterStart = (start == null || !createdAt.isBefore(start));
                    boolean beforeEnd = (end == null || !createdAt.isAfter(end));
                    return afterStart && beforeEnd;
                })
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public LeadDTO getLeadById(Long id) {
        if (id == null)
            throw new RuntimeException("Lead ID is required");
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        return convertToDTO(lead);
    }

    @Transactional
    public LeadDTO createLead(LeadDTO leadDTO) {
        if (leadDTO.getMobile() == null || leadDTO.getMobile().isEmpty()) {
            throw new RuntimeException("Mobile number is required");
        }

        if (leadDTO.getEmail() != null && !leadDTO.getEmail().isEmpty()
                && leadRepository.existsByEmail(leadDTO.getEmail())) {
            throw new RuntimeException("Lead with this email address already exists in the system");
        }

        String cleanMobile = leadDTO.getMobile().replaceAll("[^0-9]", "");
        if (leadRepository.existsByMobile(cleanMobile)) {
            throw new RuntimeException("Lead with this phone number already exists in the system");
        }

        User currentUser = userRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
        Lead lead = Lead.builder()
                .name(leadDTO.getName())
                .email(leadDTO.getEmail())
                .mobile(cleanMobile)
                .college(leadDTO.getCollege())
                .serialNumber(leadDTO.getSerialNumber())
                .status(Lead.Status.NEW)
                .assignedTo(null)
                .createdBy(currentUser)
                .build();
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updateStatus(Long id, String status, String note) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        User currentUser = userRepository.findById(getCurrentUserId()).orElse(null);

        Lead.Status newStatusEnum = Lead.Status.valueOf(status.toUpperCase());
        validateStatusTransition(lead, newStatusEnum, currentUser);

        lead.setStatus(newStatusEnum);
        lead.setNote(note);
        lead.setUpdatedBy(currentUser);

        // 2. Persistent History: LeadNote
        String noteToSave = (note == null || note.trim().isEmpty()) ? "Status updated to " + status : note;
        LeadNote leadNote = LeadNote.builder()
                .content(noteToSave)
                .status(status)
                .lead(lead)
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();
        leadNoteRepository.save(leadNote);
        if (lead.getNotes() == null)
            lead.setNotes(new java.util.ArrayList<>());
        lead.getNotes().add(leadNote);

        Lead saved = leadRepository.save(lead);

        // Handle manual conversion trigger for Student Fee Structure
        if (Lead.Status.CONVERTED.equals(saved.getStatus())) {
            try {
                // For manual conversion, we assume 0 paid if they haven't gone through payment modal
                leadPaymentService.syncStudentFee(saved, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null);
            } catch (Exception e) {
                // Log and continue
            }
        }

        return convertToDTO(saved);
    }

    @Transactional
    public LeadDTO recordCallOutcome(Long leadId, Map<String, Object> outcomeData) {
        String status = (String) outcomeData.get("status");
        String note = (String) outcomeData.get("note");
        String followUpDate = (String) outcomeData.get("followUpDate");

        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead not found"));

        User user = userRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Update Lead State
        Lead.Status newStatusEnum = Lead.Status.valueOf(status.toUpperCase());
        validateStatusTransition(lead, newStatusEnum, user);

        lead.setStatus(newStatusEnum);
        lead.setNote(note);
        lead.setUpdatedBy(user);
        if (followUpDate != null && !followUpDate.trim().isEmpty()) {
            try {
                String dateStr = followUpDate.trim();
                LocalDateTime fdt;
                
                if (dateStr.contains("/") && dateStr.contains(" ")) {
                    // Handle MM/DD/YYYY HH:mm format (standard picker)
                    String[] parts = dateStr.split(" ");
                    String[] dateParts = parts[0].split("/");
                    int month = Integer.parseInt(dateParts[0]);
                    int day = Integer.parseInt(dateParts[1]);
                    int year = Integer.parseInt(dateParts[2]);
                    
                    String[] timeParts = parts[1].split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int min = Integer.parseInt(timeParts[1]);
                    
                    // Handle AM/PM
                    if (dateStr.toUpperCase().contains("PM") && hour < 12) hour += 12;
                    if (dateStr.toUpperCase().contains("AM") && hour == 12) hour = 0;
                    
                    fdt = LocalDateTime.of(year, month, day, hour, min);
                } else {
                    // Fallback to ISO
                    if (dateStr.length() == 16) dateStr += ":00";
                    if (!dateStr.contains("T") && dateStr.contains(" ")) dateStr = dateStr.replace(" ", "T");
                    fdt = LocalDateTime.parse(dateStr);
            }
            
            lead.setFollowUpDate(fdt);
            lead.setFollowUpRequired(true);
            
            // Create task for this manual follow-up immediately
            LeadTask followUpTask = LeadTask.builder()
                    .lead(lead)
                    .title("Follow-up Call: " + lead.getName())
                    .description("Follow-up interaction scheduled via interaction portal. Note: " + (note != null ? note : "Scheduled"))
                    .dueDate(fdt)
                    .status(LeadTask.TaskStatus.PENDING)
                    .taskType("FOLLOW_UP")
                    .build();
            leadTaskRepository.save(followUpTask);
        } catch (Exception e) {
            log.warn(">>> Failed to parse manual follow-up date '{}': {}", followUpDate, e.getMessage());
        }
    }
    leadRepository.save(lead);

    // 2. Persistent History: LeadNote
    String noteToSave = (note == null || note.trim().isEmpty()) ? "Call outcome recorded: " + status : note;
    LeadNote leadNote = LeadNote.builder()
            .content(noteToSave)
            .status(status)
            .lead(lead)
            .createdBy(user)
            .createdAt(LocalDateTime.now())
            .build();
    leadNoteRepository.save(leadNote);
    if (lead.getNotes() == null)
        lead.setNotes(new java.util.ArrayList<>());
    lead.getNotes().add(leadNote);

    // 3. Persistent Audit: Create CallRecord
    CallRecord record = CallRecord.builder()
            .lead(lead)
            .user(user)
            .phoneNumber(lead.getMobile())
            .callType("OUTGOING")
            .status(status)
            .notes(note)
            .startTime(LocalDateTime.now(INDIA_ZONE))
            .endTime(LocalDateTime.now(INDIA_ZONE))
            .build();
    callRecordRepository.save(record);
    
    // 4. Handle conversion sync if moved to terminal PAID state
    if (Lead.Status.CONVERTED.equals(lead.getStatus()) || Lead.Status.PAID.equals(lead.getStatus()) || Lead.Status.EMI.equals(lead.getStatus())) {
        try {
            Boolean isPaymentAction = (Boolean) outcomeData.get("isPaymentAction");
            if (Boolean.TRUE.equals(isPaymentAction)) {
                java.math.BigDecimal totalAmt = new java.math.BigDecimal(outcomeData.get("totalAmount").toString());
                java.math.BigDecimal paidAmt = new java.math.BigDecimal(outcomeData.get("paidAmount").toString());
                String method = (String) outcomeData.get("paymentMethod");
                String pType = (String) outcomeData.get("paymentType");

                // A. Record the Actual Payment (Initial or Full)
                // This will internally trigger the student fee sync correctly
                Map<String, Object> paymentRecord = new HashMap<>();
                paymentRecord.put("leadId", leadId);
                paymentRecord.put("amount", paidAmt);
                paymentRecord.put("totalAmount", totalAmt);
                paymentRecord.put("paymentMethod", method);
                paymentRecord.put("paymentType", pType);
                paymentRecord.put("note", "Interaction Record: " + note);

                // Extract the next installment due date from the list if available
                if ("PART".equals(pType) || "EMI".equals(status)) {
                    List<Map<String, Object>> installments = (List<Map<String, Object>>) outcomeData.get("installments");
                    if (installments != null && !installments.isEmpty()) {
                        // Find the earliest due date for the ledger summary
                        String earliestDue = installments.stream()
                                .map(i -> (String) i.get("dueDate"))
                                .filter(d -> d != null && !d.isEmpty())
                                .sorted()
                                .findFirst()
                                .orElse(null);
                        paymentRecord.put("nextDueDate", earliestDue);
                    }
                }
                
                leadPaymentService.recordManualPayment(paymentRecord);

                // C. Handle Installments if EMI
                if ("PART".equals(pType) || "EMI".equals(status)) {
                    List<Map<String, Object>> installments = (List<Map<String, Object>>) outcomeData.get("installments");
                    if (installments != null && !installments.isEmpty()) {
                        for (Map<String, Object> inst : installments) {
                            java.math.BigDecimal instAmt = new java.math.BigDecimal(inst.get("amount").toString());
                            String dueDateStr = (String) inst.get("dueDate");
                            
                            if (dueDateStr != null && !dueDateStr.isEmpty()) {
                                // Create a PENDING payment record for future collection
                                Payment pendingPayment = Payment.builder()
                                        .leadId(leadId)
                                        .amount(instAmt)
                                        .totalAmount(totalAmt)
                                        .status(Payment.Status.PENDING)
                                        .paymentType("EMI_INSTALLMENT")
                                        .dueDate(java.time.LocalDateTime.parse(dueDateStr + "T10:00:00"))
                                        .build();
                                
                                paymentRepository.save(pendingPayment);
                                
                                // D. Create Lead Task for this installment
                                LeadTask task = LeadTask.builder()
                                        .lead(lead)
                                        .title("EMI Collection - ₹" + instAmt)
                                        .description("Automated collection task for installment scheduled via interaction portal.")
                                        .dueDate(java.time.LocalDateTime.parse(dueDateStr + "T10:00:00"))
                                        .status(LeadTask.TaskStatus.PENDING)
                                        .taskType("EMI_COLLECTION")
                                        .build();
                                leadTaskRepository.save(task);
                            }
                        }
                    }
                }
            } else {
                // Traditional fallback if no payment data was sent
                leadPaymentService.syncStudentFee(lead, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null);
            }
        } catch (Exception e) {
            log.error("Financial sync failed during recordCallOutcome: {}", e.getMessage(), e);
        }
    }

    return convertToDTO(leadRepository.save(lead));
}

    private LeadDTO convertToDTO(Lead lead) {
        LeadDTO dto = LeadDTO.fromEntity(lead);

        // Identity Link: Global financial scan (Remove status bottleneck to catch all overdue installments)
        if (lead.getId() != null) {
            List<Payment> payments = paymentRepository.findByLeadIdIn(List.of(lead.getId()));
            if (payments != null) {
                // Find the nearest pending or overdue payment
                Payment nearest = payments.stream()
                    .filter(p -> p.getStatus() == Payment.Status.PENDING || p.getStatus() == Payment.Status.OVERDUE)
                    .filter(p -> p.getDueDate() != null)
                    .min(Comparator.comparing(Payment::getDueDate))
                    .orElse(null);

                if (nearest != null) {
                    dto.setNextPaymentDueDate(nearest.getDueDate());
                    dto.setPaymentStatus(nearest.getStatus().name());
                }
            }
        }

        // Task Sync: Add profile-level flags for the dashboard
        List<LeadTask> tasks = leadTaskRepository.findByLeadId(lead.getId());
        if (tasks != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);

            dto.setHasOverdueTask(tasks.stream()
                .anyMatch(t -> t.getStatus() == LeadTask.TaskStatus.PENDING && t.getDueDate() != null && t.getDueDate().isBefore(now)));
            
            dto.setTaskDueToday(tasks.stream()
                .anyMatch(t -> t.getStatus() == LeadTask.TaskStatus.PENDING && t.getDueDate() != null && !t.getDueDate().isBefore(startOfDay) && !t.getDueDate().isAfter(endOfDay)));
        }

        return dto;
    }

    @Transactional
    public LeadDTO rejectLead(Long id, Map<String, Object> rejectionData) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        lead.setStatus(Lead.Status.LOST);
        lead.setRejectionReason((String) rejectionData.get("reason"));
        return convertToDTO(leadRepository.save(lead));
    }

    public User getCurrentUser() {
        return userRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    public List<LeadDTO> getAllLeadsForManager(Long userId) {
        User manager = getCurrentUser();
        List<Long> targetIds;

        if (userId != null) {
            // Verify if the requested userId is the manager themselves or a subordinate
            List<Long> subordinateIds = userRepository.findSubordinateIds(manager.getId());
            if (userId.equals(manager.getId()) || (subordinateIds != null && subordinateIds.contains(userId))) {
                targetIds = List.of(userId);
            } else {
                return Collections.emptyList();
            }
        } else {
            targetIds = userRepository.findSubordinateIds(manager.getId());
            targetIds.add(manager.getId());
        }

        List<User> targetUsers = userRepository.findAllById(targetIds);
        List<Lead> leads = leadRepository.findByAssignedToInOrCreatedByIn(targetUsers, targetUsers);
        leads.sort((a, b) -> {
            if (a.getCreatedAt() == null)
                return 1;
            if (b.getCreatedAt() == null)
                return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        return leads.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO assignLead(Long leadId, Long userId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead not found"));

        if (userId == null || userId == 0) {
            lead.setAssignedTo(null);
            lead.setStatus(Lead.Status.NEW);
            return convertToDTO(leadRepository.save(lead));
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        User requester = getCurrentUser();
        String requesterRole = requester.getRole() != null ? requester.getRole().getName() : "";
        Long reqId = requester.getId();
        Long targetId = targetUser.getId();

        // 1. Admin Bypass: Full System Clearance
        if ("ADMIN".equals(requesterRole)) {
            // No restrictions
        }
        // 2. Manager Protocol: Restricted to Branch Subordinates or Self
        else if ("MANAGER".equals(requesterRole)) {
            boolean isSelf = reqId.equals(targetId);
            boolean isSub = targetUser.getManager() != null && targetUser.getManager().getId().equals(reqId);
            if (!isSelf && !isSub) {
                throw new RuntimeException(
                        "Hierarchy Violation: Branch Managers are restricted to assigning leads within their own identity cluster.");
            }
        }
        // 3. Team Leader Protocol: Restricted to Squad Associates or Self
        else if ("TEAM_LEADER".equals(requesterRole)) {
            boolean isSelf = reqId.equals(targetId);
            boolean isAssoc = targetUser.getSupervisor() != null && targetUser.getSupervisor().getId().equals(reqId);
            if (!isSelf && !isAssoc) {
                throw new RuntimeException(
                        "Hierarchy Violation: Team Leaders are restricted to assigning leads within their own squad nodes.");
            }
        }
        // 4. Default: Unauthorized
        else if (!reqId.equals(targetId)) {
            throw new RuntimeException(
                    "Clearance Denied: Lead assignment requires Administrative or Squad-level permissions.");
        }

        lead.setAssignedTo(targetUser);
        // Only set to WORKING if status is NEW or null
        if (lead.getStatus() == null || lead.getStatus() == Lead.Status.NEW) {
            lead.setStatus(Lead.Status.WORKING);
        }
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public List<LeadDTO> bulkAssignLeads(List<Long> leadIds, Long userId) {
        if (leadIds == null || leadIds.isEmpty()) return Collections.emptyList();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        User requester = getCurrentUser();
        String requesterRole = requester.getRole() != null ? requester.getRole().getName() : "";
        Long reqId = requester.getId();
        Long targetId = targetUser.getId();

        // Hierarchy Clearance Validation
        if (!"ADMIN".equals(requesterRole)) {
            if ("MANAGER".equals(requesterRole)) {
                boolean isSelf = reqId.equals(targetId);
                boolean isSub = targetUser.getManager() != null && targetUser.getManager().getId().equals(reqId);
                if (!isSelf && !isSub)
                    throw new RuntimeException(
                            "Hierarchy Violation: Only branch subordinates are eligible for bulk assignment.");
            } else if ("TEAM_LEADER".equals(requesterRole)) {
                boolean isSelf = reqId.equals(targetId);
                boolean isAssoc = targetUser.getSupervisor() != null
                        && targetUser.getSupervisor().getId().equals(reqId);
                if (!isSelf && !isAssoc)
                    throw new RuntimeException(
                            "Hierarchy Violation: Only squad associates are eligible for bulk assignment.");
            } else if (!reqId.equals(targetId)) {
                throw new RuntimeException(
                        "Clearance Denied: Lead assignment protocol requires elevated administrative status.");
            }
        }

        List<Lead> leads = leadRepository.findAllById(leadIds);
        leads.forEach(l -> {
            l.setAssignedTo(targetUser);
            if (l.getStatus() == null || l.getStatus() == Lead.Status.NEW) {
                l.setStatus(Lead.Status.WORKING);
            }
        });
        return leadRepository.saveAll(leads).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO leadDTO) {
        if (id == null) throw new RuntimeException("Lead ID is required for update operation.");
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));

        if (leadDTO.getName() != null)
            lead.setName(leadDTO.getName());
        if (leadDTO.getEmail() != null)
            lead.setEmail(leadDTO.getEmail());
        if (leadDTO.getMobile() != null)
            lead.setMobile(leadDTO.getMobile());
        if (leadDTO.getCollege() != null)
            lead.setCollege(leadDTO.getCollege());
        if (leadDTO.getSerialNumber() != null)
            lead.setSerialNumber(leadDTO.getSerialNumber());
        if (leadDTO.getNote() != null)
            lead.setNote(leadDTO.getNote());

        if (lead == null) throw new RuntimeException("Failed to prepare lead for persistence.");
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updateNote(Long id, String note) {
        if (id == null) throw new RuntimeException("Lead ID required for note update.");
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        
        Long userId = getCurrentUserId();
        if (userId == null) throw new RuntimeException("Authentication failure: User ID not found.");
        User currentUser = userRepository.findById(userId).orElse(null);

        lead.setNote(note);
        lead.setUpdatedBy(currentUser);

        if (note != null && !note.isEmpty()) {
            LeadNote leadNote = LeadNote.builder()
                    .content(note)
                    .status(lead.getStatus() != null ? lead.getStatus().name() : "NEW")
                    .lead(lead)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .build();
            if (leadNote != null) {
                leadNoteRepository.save(leadNote);
            }
            if (lead.getNotes() == null)
                lead.setNotes(new java.util.ArrayList<>());
            lead.getNotes().add(leadNote);
        }

        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updatePaymentLink(Long id, String paymentLink) {
        if (id == null) throw new RuntimeException("Lead ID required for payment synchronization.");
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        lead.setPaymentLink(paymentLink);
        return convertToDTO(leadRepository.save(lead));
    }

    public List<com.lms.www.leadmanagement.dto.UserDTO> getCurrentUserSubordinates() {
        User user = getCurrentUser();
        java.util.Set<User> subordinates = new java.util.HashSet<>();
        if (user.getSubordinates() != null)
            subordinates.addAll(user.getSubordinates());
        if (user.getManagedAssociates() != null)
            subordinates.addAll(user.getManagedAssociates());
        subordinates.add(user);
        return subordinates.stream()
                .map(com.lms.www.leadmanagement.dto.UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private void validateStatusTransition(Lead lead, Lead.Status newStatus, User user) {
        if (lead.getStatus() == null || newStatus == null) return;
        
        // Admin/Manager Bypass for correction
        String role = user.getRole() != null ? user.getRole().getName() : "";
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) return;

        int currentRank = STATUS_RANK.getOrDefault(lead.getStatus(), -1);
        int newRank = STATUS_RANK.getOrDefault(newStatus, -1);

        // Rule 1: No Regression and No Same-Status (Must move forward)
        if (newRank <= currentRank && newRank != -1) {
            throw new RuntimeException("Pipeline Violation: Leads must move forward in the pipeline. Cannot move backward or stay at the same status (" + lead.getStatus() + ").");
        }

        // Rule 2: Interested Bottleneck
        if (Lead.Status.INTERESTED.equals(lead.getStatus())) {
            // Rank 4 is INTERESTED. Terminal ranks are 5 (Converted/Paid) and 6 (Lost/Closed).
            if (newRank != 4 && newRank != 5 && newRank != 6) {
                throw new RuntimeException("Operational Constraint: Interested leads must move forward to Converted or Lost.");
            }
        }
    }
}
