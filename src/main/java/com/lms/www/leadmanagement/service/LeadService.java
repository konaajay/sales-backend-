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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final CallRecordRepository callRecordRepository;
    private final LeadNoteRepository leadNoteRepository;
    private final LeadTaskRepository leadTaskRepository;
    private final PaymentRepository paymentRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final SecurityService securityService;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public Map<String, Object> getLeadStats() {
        User requester = securityService.getCurrentUser();
        List<Long> targetIds = new ArrayList<>();
        targetIds.add(requester.getId());
        targetIds.addAll(userRepository.findSubordinateIds(requester.getId()));

        LocalDateTime start = LocalDateTime.now().minusYears(1); // Or dynamic
        LocalDateTime end = LocalDateTime.now();

        // High performance aggregation via Projection
        List<DashboardProjection> projections = leadRepository.countByStatusForUsers(targetIds, start, end);
        
        Map<String, Object> stats = new HashMap<>();
        long total = 0;
        long converted = 0;
        long lost = 0;

        for (DashboardProjection p : projections) {
            long count = p.getCount();
            total += count;
            LeadStatus status = LeadStatus.fromString(p.getStatus());
            if (status == LeadStatus.CONVERTED || status == LeadStatus.PAID) converted += count;
            else if (status == LeadStatus.LOST || status == LeadStatus.NOT_INTERESTED) lost += count;
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
        
        return leadRepository.findByAssignedToInOrCreatedByIn(context, context)
                .stream()
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeadDTO> getLeadsForTeamLeader(LocalDateTime start, LocalDateTime end, Long targetUserId) {
        User tl = securityService.getCurrentUser();
        List<Long> targetIds = new ArrayList<>();

        if (targetUserId != null) {
            User target = userRepository.findById(targetUserId).orElseThrow();
            securityService.validateHierarchyAccess(tl, target);
            targetIds.add(targetUserId);
        } else {
            targetIds.addAll(userRepository.findSubordinateIds(tl.getId()));
            targetIds.add(tl.getId());
        }

        List<User> targetUsers = userRepository.findAllById(targetIds);
        return leadRepository.findByAssignedToInOrCreatedByIn(targetUsers, targetUsers).stream()
                .filter(l -> (start == null || !l.getCreatedAt().isBefore(start)) && (end == null || !l.getCreatedAt().isAfter(end)))
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO createLead(LeadDTO dto) {
        if (dto.getMobile() == null || dto.getMobile().isEmpty()) throw new InvalidRequestException("Mobile required");
        if (leadRepository.existsByMobile(dto.getMobile())) throw new InvalidRequestException("Lead already exists");

        User creator = securityService.getCurrentUser();
        Lead lead = Lead.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .mobile(dto.getMobile())
                .college(dto.getCollege())
                .status(LeadStatus.NEW.name())
                .assignedTo(creator) // Auto-assign to creator
                .createdBy(creator)
                .build();
        
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updateStatus(Long id, StatusUpdateRequest request) {
        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead not found"));
        
        // Lock status for converted/paid leads
        String currentStatus = lead.getStatus() != null ? lead.getStatus().toUpperCase() : "";
        if (List.of("PAID", "SUCCESS", "EMI").contains(currentStatus)) {
            throw new InvalidRequestException("Cannot change status of a finalized lead");
        }

        User user = securityService.getCurrentUser();
        LeadStatus newStatus = LeadStatus.fromString(request.getStatus());
        lead.setStatus(newStatus.name());
        lead.setUpdatedBy(user);

        if ("CONVERTED".equalsIgnoreCase(request.getStatus())) {
            initializeStudentFee(lead, request);
        }

        saveNote(lead, user, request.getNote(), request.getStatus());
        triggerPipelineActions(lead, newStatus, request);

        return convertToDTO(leadRepository.save(lead));
    }

    private void initializeStudentFee(Lead lead, StatusUpdateRequest request) {
        BigDecimal total = request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = request.getPaidAmount() != null ? request.getPaidAmount() : BigDecimal.ZERO;

        LocalDateTime firstDue = null;
        if (request.getNextInstallmentDate() != null && !request.getNextInstallmentDate().isEmpty()) {
            try {
                firstDue = LocalDateTime.parse(request.getNextInstallmentDate().contains("T") ? request.getNextInstallmentDate() : request.getNextInstallmentDate() + "T10:00:00");
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
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            fee.setPaidAmount(fee.getPaidAmount().add(paid));
        }
        fee.setBalanceAmount(total.subtract(fee.getPaidAmount()));
        
        // Handle full installment map if provided
        if (request.getInstallments() != null && !request.getInstallments().isEmpty()) {
            for (StatusUpdateRequest.InstallmentMap inst : request.getInstallments()) {
                LocalDateTime due = null;
                try {
                    due = LocalDateTime.parse(inst.getDueDate().contains("T") ? inst.getDueDate() : inst.getDueDate() + "T10:00:00");
                } catch (Exception e) {}
                
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

    private void saveNote(Lead lead, User creator, String content, String status) {
        LeadNote note = LeadNote.builder()
                .lead(lead)
                .createdBy(creator)
                .content(content != null ? content : "Status update: " + status)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        leadNoteRepository.save(note);
    }

    private void triggerPipelineActions(Lead lead, LeadStatus status, StatusUpdateRequest request) {
        pipelineStageRepository.findByStatusValue(status.name()).ifPresent(stage -> {
            if (stage.isCreateTask()) {
                if (stage.isRequireDate() && (request.getDueDate() == null || request.getDueDate().isEmpty())) {
                    throw new InvalidRequestException("Date required for status: " + status.name());
                }

                LocalDateTime dueDate = LocalDateTime.now().plusDays(stage.getDefaultFollowupDays());
                if (request.getDueDate() != null && !request.getDueDate().isEmpty()) {
                    try {
                        dueDate = LocalDateTime.parse(request.getDueDate().contains("T") ? request.getDueDate() : request.getDueDate() + "T10:00:00");
                    } catch (Exception e) {
                        log.warn("Invalid due date format: {}", request.getDueDate());
                    }
                }

                if (!leadTaskRepository.existsByLeadIdAndStatusAndDueDate(lead.getId(), LeadTask.TaskStatus.PENDING, dueDate)) {
                    leadTaskRepository.save(LeadTask.builder()
                            .lead(lead)
                            .title("Follow-up: " + stage.getLabel())
                            .dueDate(dueDate)
                            .status(LeadTask.TaskStatus.PENDING)
                            .taskType("FOLLOW_UP")
                            .build());
                }
            }
        });

        if ("LOST".equalsIgnoreCase(status.name()) || "NOT_INTERESTED".equalsIgnoreCase(status.name())) {
            leadTaskRepository.cancelAllPendingByLeadId(lead.getId());
        }
    }

    @Transactional
    public LeadDTO assignLead(Long leadId, Long userId) {
        Lead lead = leadRepository.findById(leadId).orElseThrow();
        User requester = securityService.getCurrentUser();

        if (userId == null || userId == 0) {
            lead.setAssignedTo(null);
            lead.setStatus(LeadStatus.NEW.name());
        } else {
            User target = userRepository.findById(userId).orElseThrow();
            securityService.validateHierarchyAccess(requester, target);
            lead.setAssignedTo(target);
            if (LeadStatus.NEW.name().equals(lead.getStatus())) lead.setStatus(LeadStatus.CONTACTED.name());
        }
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public List<LeadDTO> bulkAssignLeads(List<Long> leadIds, Long userId) {
        User target = userRepository.findById(userId).orElseThrow();
        securityService.validateHierarchyAccess(securityService.getCurrentUser(), target);

        List<Lead> leads = leadRepository.findAllById(leadIds);
        leads.forEach(l -> {
            l.setAssignedTo(target);
            if (LeadStatus.NEW.name().equals(l.getStatus())) l.setStatus(LeadStatus.CONTACTED.name());
        });
        return leadRepository.saveAll(leads).stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private LeadDTO convertToDTO(Lead lead) {
        LeadDTO dto = LeadDTO.fromEntity(lead);
        // Optimization: Lazy check or bulk fetch if needed for large lists
        return dto;
    }

    public LeadDTO getLeadById(Long id) {
        return leadRepository.findById(id).map(this::convertToDTO).orElseThrow();
    }

    public User getCurrentUser() {
        return securityService.getCurrentUser();
    }

    public List<LeadDTO> getAllLeadsForManager(Long userId) {
        User requester = securityService.getCurrentUser();
        List<User> targets = new ArrayList<>();
        
        if (userId != null) {
            User target = userRepository.findById(userId).orElseThrow();
            securityService.validateHierarchyAccess(requester, target);
            targets.add(target);
        } else {
            targets.addAll(userRepository.findAllById(userRepository.findSubordinateIds(requester.getId())));
            targets.add(requester);
        }

        return leadRepository.findByAssignedToInOrCreatedByIn(targets, targets).stream()
                .sorted(Comparator.comparing(Lead::getCreatedAt).reversed())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO dto) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        lead.setName(dto.getName());
        lead.setEmail(dto.getEmail());
        lead.setMobile(dto.getMobile());
        lead.setCollege(dto.getCollege());
        lead.setNote(dto.getNote());
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO rejectLead(Long id, Map<String, Object> data) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        lead.setStatus(LeadStatus.REJECTED.name());
        lead.setRejectionReason((String) data.get("reason"));
        lead.setRejectionNote((String) data.get("note"));
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
        lead.setNote(note);
        return convertToDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO updatePaymentLink(Long id, String link) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        lead.setPaymentLink(link);
        return convertToDTO(leadRepository.save(lead));
    }

    public List<UserDTO> getCurrentUserSubordinates() {
        User user = securityService.getCurrentUser();
        return userRepository.findAllById(userRepository.findSubordinateIds(user.getId())).stream()
                .map(UserDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO recordCallOutcome(Long id, Map<String, Object> outcomeData) {
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus((String) outcomeData.get("status"));
        request.setNote((String) outcomeData.get("note"));
        
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

        return updateStatus(id, request);
    }
}
