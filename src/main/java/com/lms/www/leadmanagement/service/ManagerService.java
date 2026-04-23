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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

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
            System.err.println("CRITICAL: Failed to send Team Leader credentials email: " + e.getMessage());
        }

        return UserDTO.fromEntity(savedUser);
    }

    public List<UserDTO> getAllManagedUsers() {
        User manager = getCurrentUser();
        List<User> subordinates = userRepository.findByManager(manager);
        
        // Only sync if no subordinates are found, to avoid overhead on every fetch
        if (subordinates.isEmpty()) {
            syncOrphanedSubordinates(manager);
            subordinates = userRepository.findByManager(manager);
        }
        
        // Include the manager themselves in the list so they are visible and selectable in the UI
        java.util.List<User> allVisible = new java.util.ArrayList<>();
        allVisible.add(manager);
        allVisible.addAll(subordinates);
        
        return allVisible.stream()
                .filter(u -> u.getRole() != null && !u.getRole().getName().equals("ADMIN"))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
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
        if (supId != null) {
            supervisor = userRepository.findById(supId).orElseThrow(() -> new RuntimeException("Supervisor not found"));
        }

        User manager = getCurrentUser();
        User user = User.builder()
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .mobile(userDTO.getMobile())
                .password(passwordEncoder.encode(userDTO.getPassword()))
                .role(role)
                .manager(manager)
                .supervisor(supervisor)
                .build();
        User savedUser = java.util.Objects.requireNonNull(userRepository.save(user));
        
        // Send Credentials to Mail
        try {
            mailService.sendUserCredentials(savedUser.getEmail(), userDTO.getPassword(), savedUser.getName());
        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to send user credentials email: " + e.getMessage());
        }

        return UserDTO.fromEntity(savedUser);
    }

    public UserDTO assignToSupervisor(Long associateId, Long supervisorId) {
        if (associateId == null || supervisorId == null) throw new IllegalArgumentException("IDs cannot be null");
        User associate = userRepository.findById(associateId).orElseThrow(() -> new RuntimeException("Associate not found"));
        User supervisor = userRepository.findById(supervisorId).orElseThrow(() -> new RuntimeException("Supervisor not found"));
        
        // Ensure both belong to the current manager
        User currentManager = getCurrentUser();
        if (!associate.getManager().getId().equals(currentManager.getId()) || 
            !supervisor.getManager().getId().equals(currentManager.getId())) {
            throw new RuntimeException("Unauthorized: User does not belong to your team");
        }

        associate.setSupervisor(supervisor);
        return UserDTO.fromEntity(userRepository.save(associate));
    }

    public List<UserDTO> bulkAssignSupervisor(List<Long> associateIds, Long supervisorId) {
        if (associateIds == null || supervisorId == null) throw new IllegalArgumentException("IDs cannot be null");
        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new RuntimeException("Supervisor not found"));
        
        User currentManager = getCurrentUser();
        if (supervisor.getManager() == null || !supervisor.getManager().getId().equals(currentManager.getId())) {
            throw new RuntimeException("Unauthorized: Supervisor does not belong to your team");
        }

        List<User> associates = userRepository.findAllById(associateIds);
        for (User associate : associates) {
            if (associate.getManager() == null || !associate.getManager().getId().equals(currentManager.getId())) {
                throw new RuntimeException("Unauthorized: Associate " + associate.getName() + " does not belong to your team");
            }
            associate.setSupervisor(supervisor);
        }
        
        return userRepository.saveAll(associates).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> bulkAssignHierarchy(Map<String, String> emailMap) {
        User curManager = getCurrentUser();
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
                if (associate.getManager() != null && associate.getManager().getId().equals(curManager.getId()) &&
                    supervisor.getManager() != null && supervisor.getManager().getId().equals(curManager.getId())) {
                    associate.setSupervisor(supervisor);
                    userRepository.save(associate);
                    success++;
                } else {
                    failure++;
                    errors.add("Security Violation: " + assocEmail + " or " + supEmail + " is outside your nodal branch.");
                }
            } else {
                failure++;
                errors.add("Mapping failed: " + assocEmail + " -> " + supEmail + " (Nodes not found)");
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
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (userDTO.getName() != null) user.setName(userDTO.getName());
        if (userDTO.getMobile() != null) user.setMobile(userDTO.getMobile());
        
        if (userDTO.getRole() != null) {
            Role role = roleRepository.findByName(userDTO.getRole())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + userDTO.getRole()));
            user.setRole(role);
        }

        if (userDTO.getSupervisorId() != null) {
            Long editSupId = userDTO.getSupervisorId();
            if (editSupId == null) throw new IllegalArgumentException("Supervisor ID cannot be null");
            User supervisor = userRepository.findById(editSupId)
                    .orElseThrow(() -> new RuntimeException("Supervisor not found: " + editSupId));
            user.setSupervisor(supervisor);
        } else if (userDTO.getSupervisorId() == null && user.getRole() != null && "ASSOCIATE".equals(user.getRole().getName())) {
            // Optional: Handle explicitly unassigning?
            // user.setSupervisor(null); 
        }

        if (userDTO.getShiftId() != null) {
            Long sId = userDTO.getShiftId();
            if (sId == null) throw new IllegalArgumentException("Shift ID cannot be null");
            AttendanceShift shift = attendanceShiftRepository.findById(sId)
                    .orElseThrow(() -> new RuntimeException("Shift not found: " + sId));
            user.setShift(shift);
        } else {
            user.setShift(null);
        }
        
        if (userDTO.getPermissions() != null) {
            System.out.println(">>> Updating permissions for user ID: " + user.getId() + " (" + user.getEmail() + ")");
            System.out.println(">>> Requested Permissions: " + userDTO.getPermissions());

            java.util.Set<Permission> direct = new java.util.HashSet<>();
            for (String p : userDTO.getPermissions()) {
                permissionRepository.findByName(p).ifPresent(direct::add);
            }
            
            boolean exactMatch = false;
            if (user.getRole() != null && user.getRole().getPermissions() != null) {
                java.util.Set<String> rps = user.getRole().getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
                System.out.println(">>> Role Permissions: " + rps);
                if (rps.size() == direct.size() && rps.containsAll(userDTO.getPermissions())) {
                    exactMatch = true;
                }
            }
            if (!exactMatch) {
                System.out.println(">>> Result: DIRECT OVERRIDE applied.");
                user.setDirectPermissions(direct);
            } else {
                System.out.println(">>> Result: MATCHES ROLE, clearing overrides.");
                user.getDirectPermissions().clear();
            }
        }

        return UserDTO.fromEntity(userRepository.save(user));
    }

    public void deleteUser(Long id) {
        if (id == null) throw new IllegalArgumentException("User ID cannot be null");
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (user == null) throw new RuntimeException("Unexpected null user instance");
        userRepository.delete(user);
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
}
