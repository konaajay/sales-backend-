package com.lms.www.leadmanagement.config;

import com.lms.www.leadmanagement.entity.Role;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.ReportScope;
import com.lms.www.leadmanagement.repository.PermissionRepository;
import com.lms.www.leadmanagement.repository.RoleRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PermissionRepository permissionRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) throws Exception {
        // Seed Hierarchical Permissions
        seedPermission("VIEW_LEADS", "Default description for VIEW_LEADS");
        seedPermission("CREATE_LEADS", "Permission to create new leads");
        seedPermission("ASSIGN_TO_TL", "Permission for Managers to assign leads to Team Leaders");
        seedPermission("ASSIGN_TO_ASSOCIATE", "Permission for Team Leaders to assign leads to Associates");
        seedPermission("UPDATE_STATUS", "Permission to move leads through the sales funnel");
        seedPermission("UPDATE_LEAD_STATUS", "Permission to update lead status");
        seedPermission("SEND_PAYMENT", "Default description for SEND_PAYMENT");
        seedPermission("VIEW_REPORTS", "Permission to view performance reports");
        seedPermission("BULK_UPLOAD", "Access to perform bulk uploads");
        seedPermission("MANAGE_USERS", "Default description for MANAGE_USERS");
        seedPermission("ASSIGN_LEADS", "Permission to assign leads to others");

        // Seed Roles with Clear-Cut Permission Mappings
        seedRole("ADMIN",
                java.util.Set.of("VIEW_LEADS", "CREATE_LEADS", "ASSIGN_TO_TL", "ASSIGN_TO_ASSOCIATE", "UPDATE_STATUS",
                        "UPDATE_LEAD_STATUS", "SEND_PAYMENT", "VIEW_REPORTS", "BULK_UPLOAD", "MANAGE_USERS",
                        "ASSIGN_LEADS"),
                ReportScope.ALL);
        seedRole("MANAGER",
                java.util.Set.of("VIEW_LEADS", "CREATE_LEADS", "ASSIGN_TO_TL", "ASSIGN_TO_ASSOCIATE", "UPDATE_STATUS",
                        "UPDATE_LEAD_STATUS", "SEND_PAYMENT", "VIEW_REPORTS", "BULK_UPLOAD", "MANAGE_USERS",
                        "ASSIGN_LEADS"),
                ReportScope.ALL);
        seedRole("TEAM_LEADER", java.util.Set.of("VIEW_LEADS", "CREATE_LEADS", "ASSIGN_TO_ASSOCIATE", "UPDATE_STATUS",
                "UPDATE_LEAD_STATUS", "SEND_PAYMENT", "VIEW_REPORTS", "BULK_UPLOAD"), ReportScope.TEAM);
        seedRole("ASSOCIATE", java.util.Set.of("VIEW_LEADS", "CREATE_LEADS", "UPDATE_STATUS", "UPDATE_LEAD_STATUS",
                "VIEW_REPORTS", "BULK_UPLOAD"), ReportScope.OWN);
        seedRole("USER", java.util.Set.of("VIEW_LEADS"), ReportScope.OWN);

        if (!userRepository.existsByEmail("admin@lms.com")) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN", null)));
            User admin = User.builder()
                    .name("Admin")
                    .email("admin@lms.com")
                    .mobile("+91 00000 00000")
                    .password(passwordEncoder.encode("admin123"))
                    .role(adminRole)
                    .reportScope(ReportScope.ALL)
                    .active(true)
                    .build();
            userRepository.save(admin);
            userRepository.flush();
            System.out.println("Default ADMIN created: admin@lms.com / admin123");
        } else {
            // Update existing admin
            User admin = userRepository.findByEmail("admin@lms.com")
                    .orElseGet(() -> {
                        User newUser = new User();
                        newUser.setName("System Admin");
                        newUser.setEmail("admin@lms.com");
                        newUser.setMobile("+91 00000 00000");
                        newUser.setPassword(passwordEncoder.encode("admin123"));
                        return userRepository.save(newUser);
                    });
            roleRepository.findByName("ADMIN").ifPresent(role -> {
                admin.setRole(role);
                admin.setReportScope(com.lms.www.leadmanagement.entity.ReportScope.ALL);
                admin.setActive(true);
                userRepository.save(admin);
                userRepository.flush();
            });
        }

        // Cleanup: Remove permissions 6, 7, 8 (or specific names) if they exist
        java.util.List.of("UPDATE_PAYMENT", "VIEW_PROFILE", "VIEW_COURSES").forEach(pName -> {
            permissionRepository.findByName(pName).ifPresent(p -> {
                // Remove from Roles first (implicitly handled by Cascade if mapped, but let's
                // be safe)
                roleRepository.findAll().forEach(r -> {
                    if (r.getPermissions().remove(p)) {
                        roleRepository.save(r);
                    }
                });
                permissionRepository.delete(p);
                permissionRepository.flush();
                System.out.println("Cleaned up permission: " + pName);
            });
        });
    }

    private void seedPermission(String name, String desc) {
        if (permissionRepository.findByName(name).isEmpty()) {
            permissionRepository.save(com.lms.www.leadmanagement.entity.Permission.builder()
                    .name(name)
                    .description(desc)
                    .build());
            permissionRepository.flush();
        }
    }

    private void seedRole(String name, java.util.Set<String> perms,
            com.lms.www.leadmanagement.entity.ReportScope defaultScope) {
        java.util.Set<com.lms.www.leadmanagement.entity.Permission> dbPerms = perms.stream()
                .map(p -> permissionRepository.findByName(p).orElseGet(() -> {
                    // Create a default permission if not found, or throw an exception if that's the
                    // desired behavior
                    // For now, let's assume it should exist, or create a placeholder.
                    // Based on the instruction, it implies handling non-existence gracefully.
                    // The provided snippet for line 72 suggests creating a default permission.
                    return permissionRepository.save(com.lms.www.leadmanagement.entity.Permission.builder()
                            .name(p)
                            .description("Default description for " + p)
                            .build());
                }))
                .collect(java.util.stream.Collectors.toSet());

        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(Role.builder().name(name).permissions(dbPerms).build());
            roleRepository.flush();
        } else {
            Role role = roleRepository.findByName(name)
                    .orElseThrow(() -> new RuntimeException("Role " + name + " not found after check"));
            role.setPermissions(dbPerms);
            roleRepository.save(role);
            roleRepository.flush();
        }
    }
}
