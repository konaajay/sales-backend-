package com.lms.www.leadmanagement.security;

import com.lms.www.leadmanagement.service.UserSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SessionActivityFilter extends OncePerRequestFilter {

    @Autowired
    @Lazy
    private UserSessionService userSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            boolean active = userSessionService.updateActivity(userDetails.getId());
            if (!active) {
                System.out.println(">>> [SESSION_WARNING] No active/fresh DB session for user " + userDetails.getId()
                        + ". Proceeding with JWT-only auth.");
            }
        }

        filterChain.doFilter(request, response);
    }
}
