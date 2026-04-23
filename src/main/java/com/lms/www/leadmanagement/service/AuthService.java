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
}
