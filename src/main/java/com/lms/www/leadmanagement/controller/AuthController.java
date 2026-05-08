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
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserSessionService userSessionService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        System.out.println("[AUTH] Login attempt for user: " + loginRequest.getEmail());
        try {
            AuthResponse response = authService.authenticateUser(loginRequest, request);
            System.out.println("[AUTH] Login successful for: " + loginRequest.getEmail());
            System.out.println("[AUTH] Role in Response: " + response.getRole());
            System.out.println("[AUTH] User ID: " + response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("[AUTH] Login failed for: " + loginRequest.getEmail() + " | Reason: " + e.getMessage());
            throw e;
        }
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

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> request) {
        authService.processForgotPassword(request.get("email"));
        return ResponseEntity.ok(Map.of("message", "If an active account exists, an OTP has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> request) {
        authService.resetPasswordWithOtp(request.get("email"), request.get("otp"), request.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PutMapping("/profile")
    public ResponseEntity<com.lms.www.leadmanagement.dto.UserDTO> updateProfile(@RequestBody Map<String, String> updates) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(authService.updateProfile(email, updates));
    }
}
