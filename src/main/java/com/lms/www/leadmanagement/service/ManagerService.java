package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.entity.Permission;
import com.lms.www.leadmanagement.entity.Role;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.AttendanceShift;
import com.lms.www.leadmanagement.repository.AttendanceShiftRepository;
import com.lms.www.leadmanagement.repository.PermissionRepository;
import com.lms.www.leadmanagement.repository.RoleRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.repository.OfficeLocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@lombok.extern.slf4j.Slf4j
@Service
@Transactional
public class ManagerService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private AttendanceShiftRepository attendanceShiftRepository;
    
    @Autowired
    private OfficeLocationRepository officeLocationRepository;

    @Autowired
    private SecurityService securityService;

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDTO createTeamLeader(UserDTO userDTO) {
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email already exists: " + userDTO.getEmail());
        }
        Role tlRole = roleRepository.findByName("TEAM_LEADER").orElseThrow(() -> new RuntimeException("Role TEAM_LEADER not found"));
        User manager = getCurrentUser();
        User user = User.builder()
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .mobile(userDTO.getMobile())
                .password(passwordEncoder.encode(userDTO.getPassword()))
                .role(tlRole)
                .manager(manager)
                .build();
        User savedUser = java.util.Objects.requireNonNull(userRepository.save(user));
        
        // Send Credentials to Mail
        try {
            mailService.sendUserCredentials(savedUser.getEmail(), userDTO.getPassword(), savedUser.getName());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send Team Leader credentials email for {}: {}", savedUser.getEmail(), e.getMessage());
        }

        return UserDTO.fromEntity(savedUser);
    }

    public List<UserDTO> getAllManagedUsers() {
        User manager = getCurrentUser();
        List<Long> subordinateIds = userRepository.findSubordinateIds(manager.getId());
        List<User> subordinates = userRepository.findAllById(subordinateIds);
        
        // Include manager themselves so assignments to self show up correctly in lookups
        List<UserDTO> allUsers = new java.util.ArrayList<>();
        allUsers.add(UserDTO.fromEntity(manager));
        
        allUsers.addAll(subordinates.stream()
                .filter(u -> u.getRole() != null && !u.getRole().getName().equals("ADMIN"))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList()));
        
        return allUsers;
    }

    private void syncOrphanedSubordinates(User manager) {
        // Find users without a manager but with roles that should belong to someone
        List<User> orphans = userRepository.findAll().stream()
                .filter(u -> u.getManager() == null && u.getRole() != null && !u.getRole().getName().equals("ADMIN"))
                .filter(u -> !u.getId().equals(manager.getId())) // Avoid setting a user as their own manager (Infinite Recursion Fix)
                .collect(Collectors.toList());
        
        if (!orphans.isEmpty()) {
            orphans.forEach(u -> u.setManager(manager));
            userRepository.saveAll(orphans);
        }
    }

    public UserDTO createUser(UserDTO userDTO) {
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email already exists: " + userDTO.getEmail());
        }
        Role role = roleRepository.findByName(userDTO.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found: " + userDTO.getRole()));

        User supervisor = null;
        Long supId = userDTO.getSupervisorId();
        User manager = securityService.getCurrentUser();

        if (supId != null) {
            securityService.validateAccess(manager, supId);
            supervisor = userRepository.findById(supId).orElseThrow(() -> new RuntimeException("Supervisor not found"));
        }
        User user = User.builder()
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .mobile(userDTO.getMobile())
                .password(passwordEncoder.encode(userDTO.getPassword()))
                .role(role)
                .manager(manager)
                .supervisor(supervisor)
                .joiningDate(userDTO.getJoiningDate())
                .build();

        if (userDTO.getShiftId() != null) {
            attendanceShiftRepository.findById(userDTO.getShiftId()).ifPresent(user::setShift);
        }
        if (userDTO.getOfficeId() != null) {
            officeLocationRepository.findById(userDTO.getOfficeId()).ifPresent(user::setAssignedOffice);
        }
        User savedUser = java.util.Objects.requireNonNull(userRepository.save(user));
        
        // Send Credentials to Mail
        try {
            mailService.sendUserCredentials(savedUser.getEmail(), userDTO.getPassword(), savedUser.getName());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send user credentials email for {}: {}", savedUser.getEmail(), e.getMessage());
        }

        return UserDTO.fromEntity(savedUser);
    }

    public UserDTO assignToSupervisor(Long associateId, Long supervisorId) {
        if (associateId == null || supervisorId == null) throw new IllegalArgumentException("IDs cannot be null");
        User associate = userRepository.findById(associateId).orElseThrow(() -> new RuntimeException("Associate not found"));
        User supervisor = userRepository.findById(supervisorId).orElseThrow(() -> new RuntimeException("Supervisor not found"));
        
        // Ensure both belong to the current manager's hierarchy
        User currentManager = securityService.getCurrentUser();
        securityService.validateAccess(currentManager, associateId);
        securityService.validateAccess(currentManager, supervisorId);

        associate.setSupervisor(supervisor);
        return UserDTO.fromEntity(userRepository.save(associate));
    }

    public List<UserDTO> bulkAssignSupervisor(List<Long> associateIds, Long supervisorId) {
        if (associateIds == null || supervisorId == null) throw new IllegalArgumentException("IDs cannot be null");
        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new RuntimeException("Supervisor not found"));
        
        User currentManager = securityService.getCurrentUser();
        securityService.validateAccess(currentManager, supervisorId);

        List<User> associates = userRepository.findAllById(associateIds);
        for (User associate : associates) {
            securityService.validateAccess(currentManager, associate.getId());
            associate.setSupervisor(supervisor);
        }
        
        return userRepository.saveAll(associates).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> bulkAssignHierarchy(Map<String, String> emailMap) {
        User curManager = securityService.getCurrentUser();
        int success = 0;
        int failure = 0;
        List<String> errors = new java.util.ArrayList<>();
        
        for (Map.Entry<String, String> entry : emailMap.entrySet()) {
            String assocEmail = entry.getKey();
            String supEmail = entry.getValue();
            
            Optional<User> assocOpt = userRepository.findByEmail(assocEmail);
            Optional<User> supOpt = userRepository.findByEmail(supEmail);
            
            if (assocOpt.isPresent() && supOpt.isPresent()) {
                User associate = assocOpt.get();
                User supervisor = supOpt.get();
                
                // Safety: check manage rights
                try {
                    securityService.validateAccess(curManager, associate.getId());
                    securityService.validateAccess(curManager, supervisor.getId());
                    associate.setSupervisor(supervisor);
                    userRepository.save(associate);
                    success++;
                } catch (Exception e) {
                    failure++;
                    errors.add("Security Violation: " + assocEmail + " or " + supEmail + " is outside your branch.");
                }
            } else {
                failure++;
                errors.add("Mapping failed: " + assocEmail + " -> " + supEmail + " (Leads not found)");
            }
        }
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", success);
        result.put("failureCount", failure);
        result.put("errors", errors);
        return result;
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        if (id == null) throw new IllegalArgumentException("User ID cannot be null");
        User curUser = securityService.getCurrentUser();
        securityService.validateAccess(curUser, id);
        
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (userDTO.getName() != null) user.setName(userDTO.getName());
        if (userDTO.getMobile() != null) user.setMobile(userDTO.getMobile());
        if (userDTO.getJoiningDate() != null) user.setJoiningDate(userDTO.getJoiningDate());

        
        if (userDTO.getRole() != null) {
            Role role = roleRepository.findByName(userDTO.getRole())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + userDTO.getRole()));
            user.setRole(role);
        }

        // Handle Supervisor
        if (userDTO.getSupervisorId() != null) {
            Long editSupId = userDTO.getSupervisorId();
            securityService.validateAccess(curUser, editSupId);
            User supervisor = userRepository.findById(editSupId)
                    .orElseThrow(() -> new RuntimeException("Supervisor not found: " + editSupId));
            user.setSupervisor(supervisor);
        } else {
            user.setSupervisor(null);
        }

        // Handle Shift
        user.setShift(userDTO.getShiftId() != null ? attendanceShiftRepository.findById(userDTO.getShiftId()).orElse(null) : null);
        
        // Handle Office - Using explicit null check
        if (userDTO.getOfficeId() != null) {
            user.setAssignedOffice(officeLocationRepository.findById(userDTO.getOfficeId()).orElse(null));
        } else {
            user.setAssignedOffice(null);
        }
        
        if (userDTO.getPermissions() != null) {
            java.util.Set<Permission> direct = new java.util.HashSet<>();
            for (String p : userDTO.getPermissions()) {
                permissionRepository.findByName(p).ifPresent(direct::add);
            }
            
            boolean exactMatch = false;
            if (user.getRole() != null && user.getRole().getPermissions() != null) {
                java.util.Set<String> rps = user.getRole().getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
                if (rps.size() == direct.size() && rps.containsAll(userDTO.getPermissions())) {
                    exactMatch = true;
                }
            }
            if (!exactMatch) {
                user.setDirectPermissions(direct);
            } else {
                user.getDirectPermissions().clear();
            }
        }

        User savedUser = userRepository.save(user);
        return UserDTO.fromEntity(savedUser);
    }

    public void deleteUser(Long id) {
        if (id == null) throw new IllegalArgumentException("User ID cannot be null");
        User curUser = securityService.getCurrentUser();
        securityService.validateAccess(curUser, id);
        
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (user == null) throw new RuntimeException("Unexpected null user instance");
        
        if (user.getRole() != null && "ADMIN".equals(user.getRole().getName())) {
            throw new RuntimeException("CRITICAL: The System Administrator account cannot be deleted or deactivated.");
        }
        
        user.setActive(false);
        userRepository.save(user);
    }

    public List<String> getAllPermissions() {
        return permissionRepository.findAll().stream().map(Permission::getName).collect(Collectors.toList());
    }

    public List<com.lms.www.leadmanagement.dto.RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(r -> com.lms.www.leadmanagement.dto.RoleDTO.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .permissions(r.getPermissions() != null 
                            ? r.getPermissions().stream().map(Permission::getName).collect(Collectors.toList())
                            : java.util.Collections.emptyList())
                        .build())
                .collect(Collectors.toList());
    }

    public User getUserById(Long id) {
        return userRepository.findById(java.util.Objects.requireNonNull(id)).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public List<UserDTO> getDirectSubordinates() {
        User current = getCurrentUser();
        // Find users where manager_id = current.id OR supervisor_id = current.id
        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(current.getId())) // Don't include self in subordinates
                .filter(u -> (u.getManager() != null && u.getManager().getId().equals(current.getId())) || 
                             (u.getSupervisor() != null && u.getSupervisor().getId().equals(current.getId())))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<Long> getSubordinateIds(Long managerId) {
        // Legacy-compatible Java-based search to avoid WITH RECURSIVE issues on old MySQL
        java.util.Set<Long> allIds = new java.util.HashSet<>();
        java.util.List<Long> toProcess = new java.util.ArrayList<>();
        toProcess.add(managerId);
        
        int depth = 0;
        while (!toProcess.isEmpty() && depth < 10) {
            java.util.List<Long> nextLevel = new java.util.ArrayList<>();
            for (Long parentId : toProcess) {
                // Find anyone where manager_id = parentId OR supervisor_id = parentId
                java.util.List<Long> kids = userRepository.findAll().stream()
                        .filter(u -> (u.getManager() != null && u.getManager().getId().equals(parentId)) || 
                                     (u.getSupervisor() != null && u.getSupervisor().getId().equals(parentId)))
                        .map(User::getId)
                        .filter(id -> !allIds.contains(id) && !id.equals(managerId))
                        .collect(Collectors.toList());
                
                nextLevel.addAll(kids);
                allIds.addAll(kids);
            }
            toProcess = nextLevel;
            depth++;
        }
        return new java.util.ArrayList<>(allIds);
    }

    public List<UserDTO> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new java.util.ArrayList<>();
        return userRepository.findAllById(ids).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
