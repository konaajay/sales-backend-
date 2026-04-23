package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.LeadDTO;
import com.lms.www.leadmanagement.dto.RoleDTO;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.dto.DashboardStatsDTO;
import com.lms.www.leadmanagement.entity.Permission;
import com.lms.www.leadmanagement.entity.ReportScope;
import com.lms.www.leadmanagement.entity.Role;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.AttendanceShift;
import com.lms.www.leadmanagement.repository.AttendanceShiftRepository;
import com.lms.www.leadmanagement.repository.PermissionRepository;
import com.lms.www.leadmanagement.repository.RoleRepository;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.OfficeLocation;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.repository.LeadRepository;
import com.lms.www.leadmanagement.repository.OfficeLocationRepository;
import com.lms.www.leadmanagement.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Service
@Transactional
public class AdminService {

    @Autowired
    private DashboardStatsService dashboardStatsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public User getCurrentUser() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Autowired
    private AttendanceShiftRepository attendanceShiftRepository;

    @Autowired
    private LeadService leadService;

    @Autowired
    private LeadRepository leadRepository;

    public List<LeadDTO> getUnassignedLeads() {
        User currentUser = getCurrentUser();
        String role = currentUser.getRole().getName();

        List<Lead> leads;
        if ("ADMIN".equals(role)) {
            leads = leadRepository.findByAssignedToIsNull();
        } else {
            List<User> userList = new ArrayList<>();
            userList.add(currentUser);
            List<Long> subIds = userRepository.findSubordinateIds(currentUser.getId());
            if (subIds != null && !subIds.isEmpty()) {
                userList.addAll(userRepository.findAllById(subIds));
            }

            leads = leadRepository.findByAssignedToIsNull().stream()
                    .filter(l -> l.getCreatedBy() != null && userList.contains(l.getCreatedBy()))
                    .collect(Collectors.toList());
        }

        return leads.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null)

                        return 1;

                    if (b.getCreatedAt() == null)
                        return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(LeadDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private OfficeLocationRepository officeLocationRepository;

    public List<Map<String, Object>> getAllOffices() {
        return officeLocationRepository.findAll().stream()
                .map(o -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", o.getId());
                    map.put("name", o.getName());
                    map.put("latitude", o.getLatitude());
                    map.put("longitude", o.getLongitude());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public String generateResetOtp(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        String otp = String.format("%06d", new Random().nextInt(1000000));
        user.setResetOtp(otp);
        user.setResetOtpExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);
        return otp;
    }

    public void validateResetOtp(String email, String otp) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getResetOtp() == null || !user.getResetOtp().equals(otp)) {
            throw new RuntimeException("Invalid OTP");
        }
        if (user.getResetOtpExpiry() == null || user.getResetOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP Expired");
        }
        user.setResetOtp(null);
        user.setResetOtpExpiry(null);
        userRepository.save(user);
    }

    public void resetPasswordWithOtp(Long id, String otp, String newPassword) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getResetOtp() == null || !user.getResetOtp().equals(otp)) {
            throw new RuntimeException("Invalid OTP");
        }
        if (user.getResetOtpExpiry() == null || user.getResetOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP Expired");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetOtp(null);
        user.setResetOtpExpiry(null);
        userRepository.save(user);
    }

    public List<UserDTO> getStaffTree() {
        User currentUser = getCurrentUser();
        String role = currentUser.getRole().getName();

        if ("ADMIN".equals(role)) {
            List<User> roots = userRepository.findAll().stream()
                    .filter(u -> {
                        String userRole = u.getRole() != null ? u.getRole().getName() : "";
                        return (u.getManager() == null && u.getSupervisor() == null) || "ADMIN".equals(userRole);
                    })
                    .collect(Collectors.toList());

            Set<User> uniqueRoots = new HashSet<>(roots);
            return uniqueRoots.stream()
                    .map(UserDTO::fromEntityWithTree)
                    .collect(Collectors.toList());
        } else {
            // Manager/TL sees their own tree branch
            return List.of(UserDTO.fromEntityWithTree(currentUser));
        }
    }

    public List<UserDTO> getManagers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "MANAGER".equals(u.getRole().getName()))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getTeamsByManager(Long managerId) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "TEAM_LEADER".equals(u.getRole().getName()))
                .filter(u -> u.getSupervisor() != null && u.getSupervisor().getId().equals(managerId))
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getAssociates(Long teamId, Long managerId) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "ASSOCIATE".equals(u.getRole().getName()))
                .filter(u -> {
                    if (teamId != null) {
                        return u.getSupervisor() != null && u.getSupervisor().getId().equals(teamId);
                    } else if (managerId != null) {
                        boolean reportsToManager = u.getSupervisor() != null
                                && u.getSupervisor().getId().equals(managerId);
                        boolean reportsToTLUnderManager = u.getSupervisor() != null
                                && u.getSupervisor().getSupervisor() != null
                                && u.getSupervisor().getSupervisor().getId().equals(managerId);
                        return reportsToManager || reportsToTLUnderManager;
                    }
                    return true;
                })
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public LeadDTO assignLead(Long leadId, Long tlId) {
        return leadService.assignLead(leadId, tlId);
    }

    public List<LeadDTO> bulkAssignLeads(List<Long> leadIds, Long tlId) {
        return leadService.bulkAssignLeads(leadIds, tlId);
    }

    public UserDTO createManager(UserDTO userDTO) {
        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseThrow(() -> new RuntimeException("Role MANAGER not found"));
        User requester = getCurrentUser();
        String requesterRole = requester.getRole().getName();

        if (!"ADMIN".equals(requesterRole)) {
            throw new RuntimeException("Hierarchy Violation: Only Admins can initialize new Branch Managers");
        }

        User user = User.builder()
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .mobile(userDTO.getMobile())
                .password(passwordEncoder.encode(userDTO.getPassword()))
                .role(managerRole)
                .build();
        return UserDTO.fromEntity(userRepository.save(user));
    }

    public List<RoleDTO> getAllRoles() {
        System.out.println("LOG: Admin/Manager fetching all roles...");
        return roleRepository.findAll().stream()
                .map(r -> RoleDTO.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .permissions(r.getPermissions() != null
                                ? r.getPermissions().stream().map(Permission::getName).collect(Collectors.toList())
                                : java.util.Collections.emptyList())
                        .build())
                .collect(Collectors.toList());
    }

    public RoleDTO createRole(RoleDTO roleDTO) {
        java.util.Set<Permission> perms = (roleDTO.getPermissions() != null)
                ? roleDTO.getPermissions().stream()
                        .map(p -> permissionRepository.findByName(p)
                                .orElseThrow(() -> new RuntimeException("Permission not found: " + p)))
                        .collect(Collectors.toSet())
                : new java.util.HashSet<>();

        Role role = roleRepository.findByName(roleDTO.getName().toUpperCase())
                .orElse(Role.builder().name(roleDTO.getName().toUpperCase()).build());

        role.setPermissions(perms);
        Role saved = roleRepository.save(role);

        return RoleDTO.builder()
                .id(saved.getId())
                .name(saved.getName())
                .permissions(saved.getPermissions() != null
                        ? saved.getPermissions().stream().map(Permission::getName).collect(Collectors.toList())
                        : java.util.Collections.emptyList())
                .build();
    }

    public java.util.List<String> getAllPermissions() {
        System.out.println("LOG: Admin/Manager fetching all permissions...");
        return permissionRepository.findAll().stream()
                .map(com.lms.www.leadmanagement.entity.Permission::getName)
                .collect(Collectors.toList());
    }

    public UserDTO createUser(UserDTO userDTO) {
        // 1. Null/Empty Validation
        String email = (userDTO.getEmail() != null) ? userDTO.getEmail().trim() : "";
        String password = userDTO.getPassword();
        String name = (userDTO.getName() != null) ? userDTO.getName().trim() : "";

        if (email.isEmpty()) {
            throw new RuntimeException("Email is required");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters long");
        }

        if (name.isEmpty()) {
            throw new RuntimeException("Name is required");
        }

        // 2. Email Format Validation
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new RuntimeException("Invalid email format: " + email);
        }

        // 3. Email & Mobile Uniqueness
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists: " + email);
        }
        if (userDTO.getMobile() != null && userRepository.existsByMobile(userDTO.getMobile())) {
            throw new RuntimeException("Mobile number already exists: " + userDTO.getMobile());
        }

        Role role = roleRepository.findByName(userDTO.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found: " + userDTO.getRole()));

        User requester = getCurrentUser();
        String requesterRole = requester.getRole().getName();
        String targetRole = role.getName();

        // Enforcement of Hierarchical Creation Law
        if ("MANAGER".equals(requesterRole)) {
            if ("ADMIN".equals(targetRole) || "MANAGER".equals(targetRole)) {
                throw new RuntimeException(
                        "Hierarchy Violation: Branch Managers are restricted to provisioning Team Leaders and BDAs only.");
            }
        } else if (!"ADMIN".equals(requesterRole)) {
            throw new RuntimeException(
                    "Insufficient Clearance: Identity provisioning requires ADMIN or MANAGER administrative level.");
        }

        // Assign default report scope based on role
        ReportScope scope = ReportScope.OWN;
        if ("ADMIN".equals(role.getName())) {
            scope = ReportScope.ALL;
        } else if ("MANAGER".equals(role.getName()) || "TEAM_LEADER".equals(role.getName())) {
            scope = ReportScope.TEAM;
        }

        User.UserBuilder userBuilder = User.builder()
                .name(name)
                .email(email.toLowerCase())
                .mobile(userDTO.getMobile())
                .password(passwordEncoder.encode(password))
                .role(role)
                .reportScope(scope)
                .joiningDate(userDTO.getJoiningDate());

        // Hierarchy Assignment during creation
        if (userDTO.getSupervisorId() != null && userDTO.getSupervisorId() != 0) {
            userRepository.findById(userDTO.getSupervisorId()).ifPresent(sup -> {
                userBuilder.supervisor(sup);
                // Auto-link Manager if the supervisor is a Team Leader
                if ("TEAM_LEADER".equals(sup.getRole().getName()) && sup.getSupervisor() != null) {
                    userBuilder.manager(sup.getSupervisor());
                }
            });
        }

        if (userDTO.getManagerId() != null && userDTO.getManagerId() != 0) {
            userRepository.findById(userDTO.getManagerId()).ifPresent(userBuilder::manager);
        }

        if (userDTO.getShiftId() != null && userDTO.getShiftId() != 0) {
            attendanceShiftRepository.findById(userDTO.getShiftId()).ifPresent(userBuilder::shift);
        }

        if (userDTO.getOfficeId() != null && userDTO.getOfficeId() != 0) {
            officeLocationRepository.findById(userDTO.getOfficeId()).ifPresent(userBuilder::assignedOffice);
        }

        User user = userBuilder.build();
        User savedUser = userRepository.save(user);

        // Send Credentials to Mail
        System.out.println(">>> ATTEMPTING TO SEND CREDENTIALS EMAIL TO: " + savedUser.getEmail());
        try {
            mailService.sendUserCredentials(savedUser.getEmail(), password, savedUser.getName());
            System.out.println(">>> EMAIL SENT SUCCESSFULLY TO: " + savedUser.getEmail());
        } catch (Exception e) {
            System.err.println(">>> CRITICAL ERROR: FAILED TO SEND USER CREDENTIALS EMAIL TO " + savedUser.getEmail());
            System.err.println(">>> ERROR MESSAGE: " + e.getMessage());
            e.printStackTrace();
        }

        return UserDTO.fromEntity(savedUser);
    }

    public Page<UserDTO> getAllUsers(Pageable pageable) {
        User currentUser = getCurrentUser();
        String role = currentUser.getRole().getName();

        if ("ADMIN".equals(role)) {
            return userRepository.findAll(pageable).map(UserDTO::fromEntity);
        }

        List<User> subordinates = new ArrayList<>();
        subordinates.add(currentUser);
        List<Long> subIds = userRepository.findSubordinateIds(currentUser.getId());
        if (subIds != null && !subIds.isEmpty()) {
            subordinates.addAll(userRepository.findAllById(subIds));
        }
        List<Long> userIds = subordinates.stream().map(User::getId).collect(Collectors.toList());

        return userRepository.findByIdIn(userIds, pageable).map(UserDTO::fromEntity);
    }

    public Page<LeadDTO> getAllLeads(Pageable pageable) {
        User currentUser = getCurrentUser();
        String role = currentUser.getRole().getName();

        if ("ADMIN".equals(role)) {
            return leadRepository.findAll(pageable).map(LeadDTO::fromEntity);
        }

        List<User> userList = new ArrayList<>();
        userList.add(currentUser);
        List<Long> subIds = userRepository.findSubordinateIds(currentUser.getId());
        if (subIds != null && !subIds.isEmpty()) {
            userList.addAll(userRepository.findAllById(subIds));
        }

        return leadRepository.findByAssignedToIn(userList, pageable).map(LeadDTO::fromEntity);
    }

    public java.util.Map<String, Long> getLeadStats() {
        User currentUser = getCurrentUser();
        String role = currentUser.getRole().getName();

        if ("ADMIN".equals(role)) {
            return leadRepository.findAll().stream()
                    .filter(l -> l.getStatus() != null)
                    .collect(Collectors.groupingBy(l -> l.getStatus().name(), Collectors.counting()));
        }

        List<User> userList = new ArrayList<>();
        userList.add(currentUser);
        List<Long> subIds = userRepository.findSubordinateIds(currentUser.getId());
        if (subIds != null && !subIds.isEmpty()) {
            userList.addAll(userRepository.findAllById(subIds));
        }

        return leadRepository.findByAssignedToIn(userList).stream()
                .filter(l -> l.getStatus() != null)
                .collect(Collectors.groupingBy(l -> l.getStatus().name(), Collectors.counting()));
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        if (userDTO.getName() != null)
            user.setName(userDTO.getName());
        if (userDTO.getMobile() != null)
            user.setMobile(userDTO.getMobile());

        if (userDTO.getRole() != null) {
            Role role = roleRepository.findByName(userDTO.getRole())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + userDTO.getRole()));
            user.setRole(role);
        }

        if (userDTO.getManagerId() != null) {
            User manager = userRepository.findById(userDTO.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Manager not found"));

            // Cycle detection
            if (isAncestor(user.getId(), manager.getId())) {
                throw new RuntimeException("Hierarchy Violation: Circular Reporting detected.");
            }

            if (user.getId().equals(manager.getId())) {
                throw new RuntimeException("Hierarchy Violation: User cannot report to themselves.");
            }

            user.setManager(manager);
        }

        if (userDTO.getReportScope() != null) {
            // Basic validation: Associates cannot have elevated scopes
            if ("ASSOCIATE".equals(user.getRole().getName()) &&
                    userDTO.getReportScope() != ReportScope.OWN) {
                throw new RuntimeException("Associates are restricted to OWN report scope");
            }
            user.setReportScope(userDTO.getReportScope());
        }

        if (userDTO.getSupervisorId() != null && userDTO.getSupervisorId() != 0) {
            User supervisor = userRepository.findById(userDTO.getSupervisorId())
                    .orElseThrow(() -> new RuntimeException("Supervisor not found: " + userDTO.getSupervisorId()));

            // Cycle detection
            if (isAncestor(user.getId(), supervisor.getId())) {
                throw new RuntimeException(
                        "Hierarchy Violation: Circular Reporting detected. User cannot report to their own subordinate.");
            }
            if (user.getId().equals(supervisor.getId())) {
                throw new RuntimeException("Hierarchy Violation: User cannot report to themselves.");
            }

            user.setSupervisor(supervisor);

            // Auto-link Manager node for hierarchy stability
            if ("TEAM_LEADER".equals(supervisor.getRole().getName()) && supervisor.getSupervisor() != null) {
                user.setManager(supervisor.getSupervisor());
            } else if ("MANAGER".equals(supervisor.getRole().getName())) {
                user.setManager(supervisor);
            }
        } else {
            // Explicitly allow unassigning supervisor
            user.setSupervisor(null);
            user.setManager(null);
        }

        if (userDTO.getShiftId() != null) {
            AttendanceShift shift = attendanceShiftRepository.findById(userDTO.getShiftId())
                    .orElseThrow(() -> new RuntimeException("Shift not found: " + userDTO.getShiftId()));
            user.setShift(shift);
        } else {
            user.setShift(null);
        }

        if (userDTO.getOfficeId() != null) {
            OfficeLocation office = officeLocationRepository.findById(userDTO.getOfficeId())
                    .orElseThrow(() -> new RuntimeException("Office not found: " + userDTO.getOfficeId()));
            user.setAssignedOffice(office);
        } else {
            user.setAssignedOffice(null);
        }

        if (userDTO.getMonthlyTarget() != null) {
            user.setMonthlyTarget(userDTO.getMonthlyTarget());
        }

        if (userDTO.getJoiningDate() != null) {
            user.setJoiningDate(userDTO.getJoiningDate());
        }

        if (userDTO.getPermissions() != null) {
            System.out.println(">>> Updating permissions for user: " + user.getEmail());
            System.out.println(">>> New Permissions Request: " + userDTO.getPermissions());

            java.util.Set<Permission> direct = new java.util.HashSet<>();
            for (String p : userDTO.getPermissions()) {
                permissionRepository.findByName(p).ifPresent(direct::add);
            }

            boolean exactMatch = false;
            if (user.getRole() != null && user.getRole().getPermissions() != null) {
                java.util.Set<String> rps = user.getRole().getPermissions().stream().map(Permission::getName)
                        .collect(Collectors.toSet());
                if (rps.size() == direct.size() && rps.containsAll(userDTO.getPermissions())) {
                    exactMatch = true;
                }
            }

            if (!exactMatch) {
                System.out.println(">>> Applying DIRECT overrides: "
                        + direct.stream().map(Permission::getName).collect(Collectors.toList()));
                user.setDirectPermissions(direct);
            } else {
                System.out.println(">>> Permissions match Role defaults, clearing direct overrides.");
                user.getDirectPermissions().clear();
            }
        }

        return UserDTO.fromEntity(userRepository.save(user));
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        // Soft delete: toggle active state to preserve historical data
        user.setActive(!user.isActive());
        userRepository.save(user);
    }

    public Map<String, Object> getDashboardStats(LocalDateTime start, LocalDateTime end, User requester,
            Long targetUserId) {
        if (start == null)
            start = LocalDate.now().atStartOfDay();
        if (end == null)
            end = LocalDateTime.now();

        Map<String, Object> stats = new HashMap<>();
        User targetUser = requester; // Default to self

        if (targetUserId != null) {
            User target = userRepository.findById(targetUserId).orElse(null);
            if (target != null) {
                // Check if requester is allowed to view this user
                boolean allowed = requester.getRole().getName().equals("ADMIN") ||
                        isAncestorOrSelf(requester.getId(), target.getId());
                if (allowed) {
                    targetUser = target;
                }
            }
        }

        // Use unified DashboardStatsService for core metrics
        boolean includeSubordinates = (targetUserId == null);
        DashboardStatsDTO coreStats = dashboardStatsService.getStats(targetUser, start.toLocalDate(), end.toLocalDate(),
                includeSubordinates);

        if (coreStats != null) {
            stats.put("presentCount", coreStats.getPresentCount());
            stats.put("absentCount", coreStats.getAbsentCount());
            stats.put("lateCount", coreStats.getLateCount());
            stats.put("totalRevenue", coreStats.getMonthlyRevenue());
            stats.put("monthlyRevenue", coreStats.getMonthlyRevenue());
            stats.put("dailyRevenue", coreStats.getDailyRevenue());
            stats.put("expectedRevenue", coreStats.getExpectedRevenue()); // This is our 'Gap'
            stats.put("pendingRevenue", coreStats.getExpectedRevenue()); // Legacy compatibility
            stats.put("monthlyTarget", coreStats.getMonthlyTarget());
            stats.put("targetAchievement", coreStats.getTargetAchievement());
            stats.put("todayFollowups", coreStats.getTodayFollowups());
            stats.put("pendingFollowups", coreStats.getPendingFollowups());
            stats.put("totalLostCount", coreStats.getTotalLostCount());
            stats.put("totalUsers", coreStats.getTotalUsers());
            stats.put("interestedCount", coreStats.getInterestedCount());
            stats.put("interestedToday", coreStats.getInterestedToday());
        }

        // Additional admin-only stats
        if (requester.getRole().getName().equals("ADMIN")) {
            Map<String, Long> userBreakdown = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null)
                    .collect(Collectors.groupingBy(u -> u.getRole().getName(), Collectors.counting()));
            stats.put("userBreakdown", userBreakdown);
        }

        stats.put("totalGlobalLeads", leadRepository.count());
        stats.put("celebrations", new ArrayList<>()); // Placeholder for legacy UI

        return stats;
    }

    private boolean isAncestorOrSelf(Long potentialAncestorId, Long targetUserId) {
        if (potentialAncestorId.equals(targetUserId))
            return true;
        return isAncestor(potentialAncestorId, targetUserId);
    }

    public java.util.List<java.util.Map<String, Object>> getMemberPerformanceFiltered(java.time.LocalDateTime start,
            java.time.LocalDateTime end, User requester, Long targetUserId) {

        User filterUser = requester;
        if (targetUserId != null) {
            User target = userRepository.findById(targetUserId).orElse(null);
            if (target != null && isAncestorOrSelf(requester.getId(), target.getId())) {
                filterUser = target;
            }
        }
        final User targetUser = filterUser;
        if (start == null)
            start = LocalDate.now().atStartOfDay();
        if (end == null)
            end = LocalDateTime.now();

        final LocalDateTime fStart = start;
        final LocalDateTime fEnd = end;

        java.util.List<User> userList = new java.util.ArrayList<>();

        // Use identical scope logic for performance reports
        com.lms.www.leadmanagement.entity.ReportScope scope = requester.getReportScope();
        if (scope == null) {
            String roleName = (requester.getRole() != null) ? requester.getRole().getName() : "ASSOCIATE";
            if ("ADMIN".equals(roleName)) {
                scope = ReportScope.ALL;
            } else if ("MANAGER".equals(roleName) || "TEAM_LEADER".equals(roleName)) {
                scope = ReportScope.TEAM;
            } else {
                scope = ReportScope.OWN;
            }
        }
        switch (scope) {
            case OWN:
                if (requester != null)
                    userList.add(requester);
                break;
            case TEAM:
                if (targetUser != null) {
                    userList.add(targetUser);
                    List<Long> subIds = userRepository.findSubordinateIds(targetUser.getId());
                    if (subIds != null && !subIds.isEmpty()) {
                        userList.addAll(userRepository.findAllById(subIds));
                    }
                }
                break;
            case ALL:
                userList = userRepository.findAll();
                break;
        }

        if (userList.isEmpty())
            return new ArrayList<>();

        // Exclude system admins from performance reports
        List<User> filteredUsers = userList.stream()
                .filter(u -> u.getRole() != null && !"ADMIN".equals(u.getRole().getName()))
                .collect(Collectors.toList());

        if (filteredUsers.isEmpty())
            return new ArrayList<>();

        List<Long> userIds = filteredUsers.stream().map(User::getId).collect(Collectors.toList());

        // 1. Fetch Lead Interaction Base Stats
        List<Map<String, Object>> performanceStats = leadRepository.getMemberPerformanceStats(filteredUsers, fStart,
                fEnd);

        // 2. Fetch Financial Transmission Data
        List<Map<String, Object>> revenueData = paymentRepository.getRevenuePerUser(userIds, fStart, fEnd);
        List<Map<String, Object>> pendingData = paymentRepository.getPendingRevenuePerUser(userIds, fStart, fEnd);

        // Map for fast lookups

        Map<Long, BigDecimal> revenueMap = revenueData.stream()
                .collect(Collectors.toMap(m -> (Long) m.get("userId"),
                        m -> m.get("amount") != null ? (BigDecimal) m.get("amount") : BigDecimal.ZERO));
        Map<Long, Long> successMap = revenueData.stream()
                .collect(Collectors.toMap(m -> (Long) m.get("userId"),
                        m -> m.get("successCount") != null ? (Long) m.get("successCount") : 0L));
        Map<Long, BigDecimal> pendingMap = pendingData.stream()
                .collect(Collectors.toMap(m -> (Long) m.get("userId"),
                        m -> m.get("amount") != null ? (BigDecimal) m.get("amount") : BigDecimal.ZERO));
        Map<Long, BigDecimal> targetMap = filteredUsers.stream()
                .filter(u -> u.getMonthlyTarget() != null)
                .collect(Collectors.toMap(User::getId, User::getMonthlyTarget));

        // 3. Composite Merge
        return performanceStats.stream()
                .map(row -> {
                    Map<String, Object> uStats = new HashMap<>(row);
                    Long userId = (Long) uStats.get("userId");

                    BigDecimal revenue = revenueMap.getOrDefault(userId, BigDecimal.ZERO);
                    Long successCount = successMap.getOrDefault(userId, 0L);
                    BigDecimal pending = pendingMap.getOrDefault(userId, BigDecimal.ZERO);
                    BigDecimal target = targetMap.getOrDefault(userId, BigDecimal.ZERO);

                    uStats.put("revenueGenerated", revenue);
                    // Override lead-creation-based conversion count with event-based conversion count
                    uStats.put("convertedLeads", successCount);
                    uStats.put("pendingReceivables", pending);
                    uStats.put("monthlyTarget", target);

                    // Ratio derivation
                    double achievement = (target.compareTo(BigDecimal.ZERO) > 0)
                            ? revenue.multiply(new BigDecimal(100)).divide(target, 2, java.math.RoundingMode.HALF_UP)
                                    .doubleValue()
                            : 0.0;
                    uStats.put("targetAchievement", achievement);

                    uStats.keySet().forEach(key -> {
                        Object val = uStats.get(key);
                        if (val instanceof Number && !key.equals("userId") && !key.equals("revenueGenerated")
                                && !key.equals("pendingReceivables") && !key.equals("monthlyTarget")) {
                            uStats.put(key, ((Number) val).longValue());
                        }
                    });
                    return uStats;
                })
                //
                .collect(Collectors.toList());
    }

    // Hierarchy discovery now handled by UserRepository.findSubordinateIds (Safe
    // CTE)

    private boolean isAncestor(Long potentialAncestorId, Long targetUserId) {
        List<Long> subordinateIds = userRepository.findSubordinateIds(potentialAncestorId);
        return subordinateIds != null && subordinateIds.contains(targetUserId);
    }

    public List<UserDTO> getAssociatesByTl(Long tlId) {
        if (tlId == null)
            throw new IllegalArgumentException("tlId cannot be null");
        User tl = userRepository.findById(tlId)
                .orElseThrow(() -> new RuntimeException("Team Leader not found"));
        return userRepository.findBySupervisor(tl).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public UserDTO assignSupervisor(Long associateId, Long supervisorId) {
        if (associateId == null)
            throw new IllegalArgumentException("IDs cannot be null");
        User associate = userRepository.findById(associateId)
                .orElseThrow(() -> new RuntimeException("Associate not found"));

        if (supervisorId == null || supervisorId == 0) {
            associate.setSupervisor(null);
            return UserDTO.fromEntity(userRepository.save(associate));
        }

        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new RuntimeException("Supervisor not found"));

        if (associateId.equals(supervisorId)) {
            throw new RuntimeException("Hierarchy Violation: User cannot report to themselves.");
        }
        if (isAncestor(associateId, supervisorId)) {
            throw new RuntimeException("Hierarchy Violation: Circular Reporting detected.");
        }

        associate.setSupervisor(supervisor);
        return UserDTO.fromEntity(userRepository.save(associate));
    }

    public List<UserDTO> bulkAssignSupervisor(List<Long> associateIds, Long supervisorId) {
        if (associateIds == null || supervisorId == null)
            throw new IllegalArgumentException("IDs cannot be null");
        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new RuntimeException("Supervisor not found"));

        List<User> associates = userRepository.findAllById(associateIds);
        for (User a : associates) {
            if (isAncestor(a.getId(), supervisorId))
                continue;
            a.setSupervisor(supervisor);
        }

        return userRepository.saveAll(associates).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Map<String, Object> bulkMapAssociates(Map<String, String> emailMap) {
        int success = 0;
        int failure = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Map.Entry<String, String> entry : emailMap.entrySet()) {
            String associateEmail = entry.getKey();
            String supervisorEmail = entry.getValue();

            Optional<User> associateOpt = userRepository.findByEmail(associateEmail);
            Optional<User> supervisorOpt = userRepository.findByEmail(supervisorEmail);

            if (associateOpt.isPresent() && supervisorOpt.isPresent()) {
                User associate = associateOpt.get();
                User supervisor = supervisorOpt.get();

                if (associate.getId().equals(supervisor.getId())) {
                    failure++;
                    errors.add("Mapping failed: " + associateEmail + " -> " + supervisorEmail
                            + " (User cannot report to themselves)");
                    continue;
                }

                if (isAncestor(associate.getId(), supervisor.getId())) {
                    failure++;
                    errors.add("Mapping failed: " + associateEmail + " -> " + supervisorEmail
                            + " (Circular reporting detected)");
                    continue;
                }

                associate.setSupervisor(supervisor);
                userRepository.save(associate);
                success++;
            } else {
                failure++;
                errors.add("Mapping failed: " + associateEmail + " -> " + supervisorEmail
                        + " (One or both nodes not found)");
            }
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", success);
        result.put("failureCount", failure);
        result.put("errors", errors);
        return result;
    }
}
