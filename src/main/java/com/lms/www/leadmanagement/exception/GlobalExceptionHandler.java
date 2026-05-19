package com.lms.www.leadmanagement.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.Map;
import java.util.HashMap;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentialsException(org.springframework.security.authentication.BadCredentialsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Invalid email or password.");
        error.put("error", "BAD_CREDENTIALS");
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabledException(org.springframework.security.authentication.DisabledException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Your account was inactive. Please contact Admin.");
        error.put("error", "ACCOUNT_DISABLED");
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(org.springframework.security.authentication.InternalAuthenticationServiceException.class)
    public ResponseEntity<Map<String, String>> handleInternalAuthException(org.springframework.security.authentication.InternalAuthenticationServiceException ex) {
        Map<String, String> error = new HashMap<>();
        if (ex.getMessage().contains("disabled") || ex.getMessage().contains("inactive")) {
            error.put("message", "Your account was inactive. Please contact Admin.");
            error.put("error", "ACCOUNT_DISABLED");
        } else {
            error.put("message", "Authentication error: " + ex.getMessage());
            error.put("error", "AUTH_ERROR");
        }
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Security Protocol: Unauthorized Access. " + ex.getMessage());
        error.put("error", "ACCESS_DENIED");
        return ResponseEntity.status(403).body(error);
    }

    @ExceptionHandler(SecurityViolationException.class)
    public ResponseEntity<Map<String, String>> handleSecurityViolationException(SecurityViolationException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("error", "SECURITY_VIOLATION");
        return ResponseEntity.status(403).body(error);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedAccessException(UnauthorizedAccessException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("error", "UNAUTHORIZED");
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFoundException(ResourceNotFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("error", "RESOURCE_NOT_FOUND");
        return ResponseEntity.status(404).body(error);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(org.springframework.dao.DataIntegrityViolationException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.error("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, String> error = new HashMap<>();
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("Duplicate entry")) {
            error.put("message", "A record with this unique identifier (email or mobile) already exists in the system.");
            error.put("error", "DUPLICATE_RECORD");
        } else {
            error.put("message", "Database constraint violation occurred.");
            error.put("error", "DATA_INTEGRITY_VIOLATION");
        }
        return ResponseEntity.status(409).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.error("Unexpected system error at {}: ", request.getRequestURI(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("message", "An unexpected internal error occurred. Please contact system support.");
        error.put("error", "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(500).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Bad request parameter at {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("error", "BAD_REQUEST");
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequestException(InvalidRequestException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("error", "INVALID_REQUEST");
        return ResponseEntity.status(400).body(error);
    }
}
