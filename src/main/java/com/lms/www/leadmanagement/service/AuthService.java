package com.lms.www.leadmanagement.service;

import com.lms.www.leadmanagement.dto.AuthResponse;
import com.lms.www.leadmanagement.dto.LoginRequest;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

@lombok.extern.slf4j.Slf4j
@Service
@Transactional
public class AuthService {

        @Autowired
        private AuthenticationManager authenticationManager;

        @Autowired
        private JwtUtils jwtUtils;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserSessionService userSessionService;

        @Autowired
        private MailService mailService;

        public AuthResponse authenticateUser(LoginRequest loginRequest, HttpServletRequest request) {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(),
                                                loginRequest.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                String jwt = jwtUtils.generateJwtToken(authentication);

                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                User user = userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new RuntimeException("Authenticated user not found in database: "
                                                + userDetails.getUsername()));

                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty())
                        ip = request.getRemoteAddr();
                userSessionService.startSession(user, ip, request.getHeader("User-Agent"));

                return AuthResponse.builder()
                                .token(jwt)
                                .id(user.getId())
                                .email(user.getEmail())
                                .role(user.getRole().getName())
                                .name(user.getName())
                                .shiftTime(user.getShift() != null ? user.getShift().getName() + " (" + user.getShift().getStartTime() + " - " + user.getShift().getEndTime() + ")" : "Not Assigned")
                                .officeName(user.getAssignedOffice() != null ? user.getAssignedOffice().getName() : "Remote / Not Assigned")
                                .latitude(user.getAssignedOffice() != null ? user.getAssignedOffice().getLatitude() : null)
                                .longitude(user.getAssignedOffice() != null ? user.getAssignedOffice().getLongitude() : null)
                                .build();
        }

        public com.lms.www.leadmanagement.dto.UserDTO getCurrentUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new RuntimeException("No authenticated user found");
                }

                String email = (authentication.getPrincipal() instanceof UserDetails)
                                ? ((UserDetails) authentication.getPrincipal()).getUsername()
                                : authentication.getName();

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found: " + email));

                return com.lms.www.leadmanagement.dto.UserDTO.fromEntity(user);
        }

        @Transactional
        public void processForgotPassword(String email) {
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null && user.isActive()) {
                        String otp = String.format("%06d", new Random().nextInt(999999));
                        user.setResetOtp(otp);
                        user.setResetOtpExpiry(LocalDateTime.now().plusMinutes(15));
                        userRepository.save(user);
                        
                        // Send the OTP via email
                        mailService.sendOtp(email, otp, user.getName());
                } else if (user != null && !user.isActive()) {
                        log.warn("Forgot password failed: Account inactive for {}", email);
                        throw new RuntimeException("Your account is inactive. Please contact Admin.");
                } else {
                        log.warn("Forgot password failed: Email not found: {}", email);
                        throw new RuntimeException("Email not found in our registry.");
                }
        }

        @Transactional
        public void resetPasswordWithOtp(String email, String otp, String newPassword) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (!user.isActive()) {
                        throw new RuntimeException("Account is inactive");
                }

                if (user.getResetOtp() == null || !user.getResetOtp().equals(otp)) {
                        throw new RuntimeException("Invalid OTP");
                }

                if (user.getResetOtpExpiry() == null || user.getResetOtpExpiry().isBefore(LocalDateTime.now())) {
                        throw new RuntimeException("OTP has expired");
                }

                user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(newPassword));
                user.setResetOtp(null);
                user.setResetOtpExpiry(null);
                userRepository.save(user);
        }

        @Transactional
        public com.lms.www.leadmanagement.dto.UserDTO updateProfile(String email, Map<String, String> updates) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (updates.containsKey("name")) {
                        user.setName(updates.get("name"));
                }
                if (updates.containsKey("mobile")) {
                        String newMobile = updates.get("mobile");
                        if (!newMobile.equals(user.getMobile()) && userRepository.existsByMobile(newMobile)) {
                                throw new RuntimeException("Mobile number already in use by another account");
                        }
                        user.setMobile(newMobile);
                }

                userRepository.save(user);
                return com.lms.www.leadmanagement.dto.UserDTO.fromEntity(user);
        }
}
