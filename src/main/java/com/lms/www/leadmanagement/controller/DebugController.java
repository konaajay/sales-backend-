package com.lms.www.leadmanagement.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = "*")
public class DebugController {

    @GetMapping("/auth")
    public Map<String, Object> debugAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new HashMap<>();
        if (auth != null) {
            response.put("principal", auth.getName());
            response.put("authorities", auth.getAuthorities().stream()
                .map(java.lang.Object::toString)
                .collect(Collectors.toList()));
            response.put("authenticated", auth.isAuthenticated());
            response.put("details", auth.getDetails());
        } else {
            response.put("message", "No authentication found in context");
        }
        return response;
    }
}

