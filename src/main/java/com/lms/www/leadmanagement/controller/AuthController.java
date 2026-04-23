package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.AuthResponse;
import com.lms.www.leadmanagement.dto.LoginRequest;
import com.lms.www.leadmanagement.service.AuthService;
import com.lms.www.leadmanagement.service.UserSessionService;
import com.lms.www.leadmanagement.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserSessionService userSessionService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        return ResponseEntity.ok(authService.authenticateUser(loginRequest, request));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            userSessionService.endSession(((UserDetailsImpl) principal).getId());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<com.lms.www.leadmanagement.dto.UserDTO> getMe() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }
}
