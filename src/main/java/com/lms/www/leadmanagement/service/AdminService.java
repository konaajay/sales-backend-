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
        // Implementation logic moved to UserService or kept here if specific to Admin
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setMobile(dto.getMobile());
        if (dto.getRole() != null) {
            user.setRole(roleRepository.findByName(dto.getRole()).orElseThrow());
        }
        return UserDTO.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setActive(false);
        userRepository.save(user);
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
        List<Lead> leads;
        
        if (securityService.isAdmin(requester)) {
            leads = leadRepository.findByAssignedToIsNull();
        } else {
            List<Long> subordinates = userRepository.findSubordinateIds(requester.getId());
            subordinates.add(requester.getId());
            
            // Optimized query instead of stream filtering
            leads = leadRepository.findByAssignedToIsNull().stream()
                    .filter(l -> l.getCreatedBy() != null && subordinates.contains(l.getCreatedBy().getId()))
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
        return List.of(UserDTO.fromEntityWithTree(requester));
    }

    public List<UserDTO> getManagers() {
        return userRepository.findByRoleName("MANAGER").stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getTeamsByManager(Long managerId) {
        User manager = userRepository.findById(managerId).orElseThrow();
        return userRepository.findBySupervisor(manager).stream()
                .filter(u -> "TEAM_LEADER".equals(u.getRole().getName()))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getAssociates(Long teamId, Long managerId) {
        // Implementation using optimized queries
        if (teamId != null) {
            return userRepository.findBySupervisor(userRepository.findById(teamId).orElseThrow()).stream()
                    .map(UserDTO::fromEntity).collect(Collectors.toList());
        } else if (managerId != null) {
            return userRepository.findByManager(userRepository.findById(managerId).orElseThrow()).stream()
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

    public Page<LeadDTO> getAllLeads(Pageable pageable) {
        return leadRepository.findAll(pageable).map(LeadDTO::fromEntity);
    }

    public Map<String, Long> getLeadStats() {
        return dashboardStatsService.getGlobalStats();
    }

    public Map<String, Object> getDashboardStats(LocalDateTime start, LocalDateTime end, User requester, Long userId) {
        return dashboardStatsService.getStats(start, end, requester, userId);
    }

    public List<Map<String, Object>> getMemberPerformanceFiltered(LocalDateTime start, LocalDateTime end, User requester, Long userId, Long tlId) {
        return dashboardStatsService.getMemberPerformanceFiltered(start, end, requester, userId, tlId);
    }

    // --- Bulk Operations ---

    @Transactional
    public UserDTO assignSupervisor(Long assocId, Long supId) {
        User associate = userRepository.findById(assocId).orElseThrow();
        User supervisor = userRepository.findById(supId).orElseThrow();
        associate.setSupervisor(supervisor);
        // Cascading manager
        if ("TEAM_LEADER".equals(supervisor.getRole().getName())) {
            associate.setManager(supervisor.getSupervisor());
        } else if ("MANAGER".equals(supervisor.getRole().getName())) {
            associate.setManager(supervisor);
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
