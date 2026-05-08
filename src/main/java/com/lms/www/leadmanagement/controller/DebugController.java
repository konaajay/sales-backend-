package com.lms.www.leadmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DebugController {
    @GetMapping("/api/debug/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("SERVER IS RELOADING - TIMESTAMP: " + System.currentTimeMillis());
    }
}
