package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.RoleDTO;
import com.lms.www.leadmanagement.dto.UserDTO;
import com.lms.www.leadmanagement.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final AdminService adminService;

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping
    public ResponseEntity<Page<UserDTO>> getAllUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllUsers(pageable));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(adminService.createUser(userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(adminService.updateUser(id, userDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        adminService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        return ResponseEntity.ok(adminService.getAllRoles());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @PostMapping("/roles")
    public ResponseEntity<RoleDTO> createRole(@RequestBody RoleDTO roleDTO) {
        return ResponseEntity.ok(adminService.createRole(roleDTO));
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/permissions")
    public ResponseEntity<List<String>> getAllPermissions() {
        return ResponseEntity.ok(adminService.getAllPermissions());
    }

    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @GetMapping("/team-leaders")
    public ResponseEntity<List<UserDTO>> getTeamLeaders() {
        // Migration from AdminController
        return ResponseEntity.ok(adminService.getAllUsers(Pageable.unpaged()).getContent().stream()
                .filter(u -> "TEAM_LEADER".equals(u.getRole()))
                .toList());
    }
}
