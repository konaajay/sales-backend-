package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.*;
import com.lms.www.leadmanagement.entity.*;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserService userService;
    private final LeadService leadService;
    private final DashboardStatsService dashboardStatsService;
    private final SecurityService securityService;
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final LeadRepository leadRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final AttendanceShiftRepository attendanceShiftRepository;
    private final PasswordEncoder passwordEncoder;

    public List<OfficeLocationDTO> getAllOffices() {
        return officeLocationRepository.findAll().stream()
                .map(o -> OfficeLocationDTO.builder()
                        .id(o.getId())
                        .name(o.getName())
                        .latitude(o.getLatitude())
                        .longitude(o.getLongitude())
                        .radius(o.getRadius())
                        .build())
                .collect(Collectors.toList());
    }

    public User getCurrentUser() {
        return securityService.getCurrentUser();
    }

    // --- User Management Delegations ---

    @Transactional
    public UserDTO createManager(UserDTO dto) {
        return userService.createUser(dto);
    }

    @Transactional
    public UserDTO createUser(UserDTO dto) {
        return userService.createUser(dto);
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO dto) {
        log.info("Updating user {} with data: {}", id, dto);
        User requester = securityService.getCurrentUser();
        securityService.validateAccess(requester, id);

        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setMobile(dto.getMobile());
        user.setJoiningDate(dto.getJoiningDate());

        
        if (dto.getRole() != null) {
            user.setRole(roleRepository.findByName(dto.getRole()).orElseThrow());
        }
        
        // Handle Supervisor/Reports To
        if (dto.getSupervisorId() != null) {
            user.setSupervisor(userRepository.findById(dto.getSupervisorId()).orElse(null));
        } else {
            user.setSupervisor(null);
        }

        // Handle Shift
        if (dto.getShiftId() != null) {
            user.setShift(attendanceShiftRepository.findById(dto.getShiftId()).orElse(null));
        } else {
            user.setShift(null);
        }

        // Handle Office - Using explicit null check to allow clearing office
        if (dto.getOfficeId() != null) {
            user.setAssignedOffice(officeLocationRepository.findById(dto.getOfficeId()).orElse(null));
        } else {
            user.setAssignedOffice(null);
        }

        user.setActive(dto.isActive());
        
        User savedUser = userRepository.save(user);
        log.info("User {} updated successfully. Assigned Office: {}", id, 
            savedUser.getAssignedOffice() != null ? savedUser.getAssignedOffice().getName() : "None");
            
        return UserDTO.fromEntity(savedUser);
    }

    @Transactional
    public void deactivateUser(Long id) {
        User requester = securityService.getCurrentUser();
        securityService.validateAccess(requester, id);
        
        User user = userRepository.findById(id).orElseThrow();
        user.setActive(false);
        userRepository.save(user);
        leadRepository.unassignNonFinalizedLeads(id);
    }

    @Transactional
    public String generateResetOtp(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String otp = String.format("%06d", new Random().nextInt(1000000));
        user.setResetOtp(otp);
        user.setResetOtpExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);
        return otp;
    }

    @Transactional
    public void resetPasswordWithOtp(Long id, String otp, String newPassword) {
        User user = userRepository.findById(id).orElseThrow();
        if (user.getResetOtp() == null || !user.getResetOtp().equals(otp) || 
            user.getResetOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invalid or expired OTP");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetOtp(null);
        userRepository.save(user);
    }

    // --- Lead Management Delegations ---

    @Transactional(readOnly = true)
    public List<LeadDTO> getUnassignedLeads() {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> allowedIds = securityService.getAllowedUserIds(requester);
        
        List<Lead> leads = leadRepository.findByAssignedToIsNull();
        
        if (!securityService.isAdmin(requester)) {
            leads = leads.stream()
                    .filter(l -> l.getCreatedBy() != null && allowedIds.contains(l.getCreatedBy().getId()))
                    .collect(Collectors.toList());
        }
        return leads.stream().map(LeadDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public LeadDTO assignLead(Long leadId, Long tlId) {
        return leadService.assignLead(leadId, tlId);
    }

    @Transactional
    public List<LeadDTO> bulkAssignLeads(List<Long> leadIds, Long tlId) {
        return leadService.bulkAssignLeads(leadIds, tlId);
    }

    // --- Hierarchy & Stats ---

    public List<UserDTO> getStaffTree() {
        User requester = securityService.getCurrentUser();
        if (securityService.isAdmin(requester)) {
            return userRepository.findHierarchyRoots().stream()
                    .map(UserDTO::fromEntityWithTree)
                    .collect(Collectors.toList());
        }
        // Managers only see their subtree
        return List.of(UserDTO.fromEntityWithTree(requester));
    }

    public List<UserDTO> getManagers() {
        User requester = securityService.getCurrentUser();
        if (securityService.isAdmin(requester)) {
            return userRepository.findByRoleName("MANAGER").stream()
                    .map(UserDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        // Managers only see themselves
        return List.of(UserDTO.fromEntity(requester));
    }

    public List<UserDTO> getTeamsByManager(Long managerId) {
        User requester = securityService.getCurrentUser();
        
        // If managerId is null, use requester's ID if they are a manager
        Long targetId = (managerId == null) ? requester.getId() : managerId;
        securityService.validateAccess(requester, targetId);
        
        User manager = userRepository.findById(targetId).orElseThrow();
        return userRepository.findBySupervisor(manager).stream()
                .filter(u -> "TEAM_LEADER".equals(u.getRole().getName()))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getAssociates(Long teamId, Long managerId) {
        User requester = securityService.getCurrentUser();
        java.util.Set<Long> allowedIds = securityService.getAllowedUserIds(requester);
        
        if (teamId != null) {
            securityService.validateAccess(requester, teamId);
            return userRepository.findBySupervisor(userRepository.findById(teamId).orElseThrow()).stream()
                    .filter(u -> allowedIds.contains(u.getId()))
                    .map(UserDTO::fromEntity).collect(Collectors.toList());
        } else if (managerId != null) {
            securityService.validateAccess(requester, managerId);
            return userRepository.findByManager(userRepository.findById(managerId).orElseThrow()).stream()
                    .filter(u -> allowedIds.contains(u.getId()))
                    .map(UserDTO::fromEntity).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public List<UserDTO> getAssociatesByTl(Long tlId) {
        return userRepository.findBySupervisor(userRepository.findById(tlId).orElseThrow()).stream()
                .map(UserDTO::fromEntity).collect(Collectors.toList());
    }

    // --- Role & Permission ---

    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream().map(RoleDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public RoleDTO createRole(RoleDTO dto) {
        Role role = Role.builder().name(dto.getName().toUpperCase()).build();
        return RoleDTO.fromEntity(roleRepository.save(role));
    }

    public List<String> getAllPermissions() {
        return permissionRepository.findAll().stream().map(Permission::getName).collect(Collectors.toList());
    }

    // --- Stats Delegations ---

    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDTO::fromEntity);
    }

    public Page<LeadDTO> getAllLeads(Pageable pageable, Long managerId, Long teamId, Long userId, String from, String to) {
        return leadService.getAllLeadsFiltered(managerId, teamId, userId, from, to, pageable);
    }

    public Map<String, Long> getLeadStats() {
        return dashboardStatsService.getGlobalStats();
    }

    public Map<String, Object> getDashboardStats(LocalDateTime start, LocalDateTime end, User requester, Long userId, Long managerId, Long teamId) {
        java.util.Set<Long> userIds = securityService.getScopedUserIds(requester, managerId, teamId);
        
        if (userId != null) {
            securityService.validateAccess(requester, userId);
            userIds = java.util.Collections.singleton(userId);
        }
        
        DashboardStatsDTO stats = dashboardStatsService.getStats(
            userIds, 
            start.toLocalDate(), 
            end.toLocalDate(), 
            securityService.isAdmin(requester), 
            requester, 
            userId, 
            managerId != null ? managerId : teamId
        );
        
        // Manual conversion to Map for backward compatibility with Controller
        Map<String, Object> map = new HashMap<>();
        map.put("presentCount", stats.getPresentCount());
        map.put("absentCount", stats.getAbsentCount());
        map.put("lateCount", stats.getLateCount());
        map.put("halfDayCount", stats.getHalfDayCount());
        map.put("dailyRevenue", stats.getDailyRevenue());
        map.put("monthlyRevenue", stats.getMonthlyRevenue());
        map.put("targetAchievement", stats.getTargetAchievement());
        map.put("totalLeads", stats.getTotalLeads());
        map.put("convertedCount", stats.getConvertedCount());
        map.put("statusDistribution", stats.getStatusDistribution());
        map.put("dailyTrend", stats.getDailyTrend());
        map.put("performance", stats.getPerformance());
        map.put("totalUsers", stats.getTotalUsers());
        map.put("monthlyTarget", stats.getMonthlyTarget());
        map.put("pendingRevenueAmount", stats.getPendingRevenueAmount());
        return map;
    }

    public List<Map<String, Object>> getMemberPerformanceFiltered(LocalDateTime start, LocalDateTime end, User requester, Long userId, Long tlId, Long managerId) {
        return dashboardStatsService.getMemberPerformanceFiltered(start, end, requester, userId, tlId, managerId);
    }

    // --- Bulk Operations ---

    @Transactional
    public UserDTO assignSupervisor(Long assocId, Long supId) {
        User requester = securityService.getCurrentUser();
        securityService.validateAccess(requester, assocId);
        securityService.validateAccess(requester, supId);

        User associate = userRepository.findById(assocId).orElseThrow();
        User supervisor = userRepository.findById(supId).orElseThrow();
        associate.setSupervisor(supervisor);
        
        if (supervisor.getRole() != null) {
            if ("TEAM_LEADER".equals(supervisor.getRole().getName())) {
                associate.setManager(supervisor.getSupervisor());
            } else if ("MANAGER".equals(supervisor.getRole().getName())) {
                associate.setManager(supervisor);
            }
        }
        return UserDTO.fromEntity(userRepository.save(associate));
    }

    @Transactional
    public List<UserDTO> bulkAssignSupervisor(List<Long> associateIds, Long supervisorId) {
        User supervisor = userRepository.findById(supervisorId).orElseThrow();
        List<User> associates = userRepository.findAllById(associateIds);
        associates.forEach(a -> {
            a.setSupervisor(supervisor);
            if ("TEAM_LEADER".equals(supervisor.getRole().getName())) a.setManager(supervisor.getSupervisor());
            else if ("MANAGER".equals(supervisor.getRole().getName())) a.setManager(supervisor);
        });
        return userRepository.saveAll(associates).stream().map(UserDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> bulkMapAssociates(Map<String, String> emailMap) {
        // Implementation...
        return Map.of("processed", emailMap.size());
    }
}
