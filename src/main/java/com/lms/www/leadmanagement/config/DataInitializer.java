package com.lms.www.leadmanagement.config;

import com.lms.www.leadmanagement.entity.Role;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.ReportScope;
import com.lms.www.leadmanagement.entity.Permission;
import com.lms.www.leadmanagement.repository.PermissionRepository;
import com.lms.www.leadmanagement.repository.RoleRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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

    @Value("${lms.admin.email}")
    private String adminEmail;

    @Value("${lms.admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 1. Optimized Permission Seeding (Load all once)
        Map<String, String> permissionsToSeed = new LinkedHashMap<>();
        permissionsToSeed.put("VIEW_LEADS", "Default description for VIEW_LEADS");
        permissionsToSeed.put("CREATE_LEADS", "Permission to create new leads");
        permissionsToSeed.put("ASSIGN_TO_TL", "Permission for Managers to assign leads to Team Leaders");
        permissionsToSeed.put("ASSIGN_TO_ASSOCIATE", "Permission for Team Leaders to assign leads to Associates");
        permissionsToSeed.put("UPDATE_STATUS", "Permission to move leads through the sales funnel");
        permissionsToSeed.put("UPDATE_LEAD_STATUS", "Permission to update lead status");
        permissionsToSeed.put("SEND_PAYMENT", "Default description for SEND_PAYMENT");
        permissionsToSeed.put("VIEW_REPORTS", "Permission to view performance reports");
        permissionsToSeed.put("BULK_UPLOAD", "Access to perform bulk uploads");
        permissionsToSeed.put("MANAGE_USERS", "Default description for MANAGE_USERS");
        permissionsToSeed.put("ASSIGN_LEADS", "Permission to assign leads to others");

        Map<String, Permission> existingPerms = permissionRepository.findAll()
                .stream().collect(Collectors.toMap(Permission::getName, p -> p));

        List<Permission> newPerms = new ArrayList<>();
        for (Map.Entry<String, String> entry : permissionsToSeed.entrySet()) {
            if (!existingPerms.containsKey(entry.getKey())) {
                newPerms.add(Permission.builder().name(entry.getKey()).description(entry.getValue()).build());
            }
        }

        if (!newPerms.isEmpty()) {
            permissionRepository.saveAll(newPerms);
            permissionRepository.flush();
            // Refresh existingPerms after save
            existingPerms.putAll(permissionRepository.findAll()
                    .stream().collect(Collectors.toMap(Permission::getName, p -> p)));
        }

        // 2. Optimized Role Seeding
        seedRole("ADMIN", Set.of("VIEW_LEADS", "CREATE_LEADS", "ASSIGN_TO_TL", "ASSIGN_TO_ASSOCIATE", "UPDATE_STATUS", "UPDATE_LEAD_STATUS", "SEND_PAYMENT", "VIEW_REPORTS", "BULK_UPLOAD", "MANAGE_USERS", "ASSIGN_LEADS"), existingPerms);
        seedRole("MANAGER", Set.of("VIEW_LEADS", "CREATE_LEADS", "ASSIGN_TO_TL", "ASSIGN_TO_ASSOCIATE", "UPDATE_STATUS", "UPDATE_LEAD_STATUS", "SEND_PAYMENT", "VIEW_REPORTS", "BULK_UPLOAD", "MANAGE_USERS", "ASSIGN_LEADS"), existingPerms);
        seedRole("TEAM_LEADER", Set.of("VIEW_LEADS", "CREATE_LEADS", "ASSIGN_TO_ASSOCIATE", "UPDATE_STATUS", "UPDATE_LEAD_STATUS", "SEND_PAYMENT", "VIEW_REPORTS", "BULK_UPLOAD"), existingPerms);
        seedRole("ASSOCIATE", Set.of("VIEW_LEADS", "CREATE_LEADS", "UPDATE_STATUS", "UPDATE_LEAD_STATUS", "VIEW_REPORTS", "BULK_UPLOAD"), existingPerms);
        seedRole("USER", Set.of("VIEW_LEADS"), existingPerms);

        // 3. Secure Admin Creation (Using @Value)
        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
            User admin = User.builder()
                    .name("System Admin")
                    .email(adminEmail)
                    .mobile("+91 00000 00000")
                    .password(passwordEncoder.encode(adminPassword))
                    .role(adminRole)
                    .reportScope(ReportScope.ALL)
                    .active(true)
                    .build();
            userRepository.save(admin);
            System.out.println("Default ADMIN initialized securely from environment.");
        }

        // 4. Optimized Cleanup
        List<String> toCleanup = List.of("UPDATE_PAYMENT", "VIEW_PROFILE", "VIEW_COURSES");
        for (String pName : toCleanup) {
            Permission p = existingPerms.get(pName);
            if (p != null) {
                roleRepository.findAll().forEach(r -> {
                    if (r.getPermissions().remove(p)) {
                        roleRepository.save(r);
                    }
                });
                permissionRepository.delete(p);
                System.out.println("Purged deprecated permission: " + pName);
            }
        }
    }

    private void seedRole(String name, Set<String> perms, Map<String, Permission> allPerms) {
        Set<Permission> dbPerms = perms.stream()
                .map(pName -> {
                    Permission p = allPerms.get(pName);
                    if (p == null) {
                        p = permissionRepository.save(Permission.builder().name(pName).description("Auto-generated").build());
                        allPerms.put(pName, p);
                    }
                    return p;
                })
                .collect(Collectors.toSet());

        Optional<Role> existingRole = roleRepository.findByName(name);
        if (existingRole.isEmpty()) {
            roleRepository.save(Role.builder().name(name).permissions(dbPerms).build());
        } else {
            Role role = existingRole.get();
            role.setPermissions(dbPerms);
            roleRepository.save(role);
        }
    }
}
