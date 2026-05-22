package com.lms.www.leadmanagement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.lms.www.leadmanagement.entity.Registration;
import com.lms.www.leadmanagement.entity.Webinar;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.RegistrationRepository;
import com.lms.www.leadmanagement.repository.WebinarRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webinars")
@Tag(name = "Webinar Management", description = "APIs for setting up and managing webinars")
@RequiredArgsConstructor
public class WebinarController {

    private final WebinarRepository webinarRepository;
    private final RegistrationRepository registrationRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/create")
    @Operation(summary = "Create a new webinar and generate form link")
    public ResponseEntity<?> createWebinar(@RequestBody Webinar webinar) {
        // 1. Validation
        if (webinar.getTitle() == null || webinar.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Webinar title is required"));
        }
        if (webinar.getEventDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event date is required"));
        }

        // 2. Set Expiry Time (Event Date at 11:59 PM)
        System.out.println("DEBUG: Creating webinar with title: " + webinar.getTitle());
        System.out.println("DEBUG: Event date: " + webinar.getEventDate());
        LocalDateTime expiryTime = webinar.getEventDate().atTime(LocalTime.of(23, 59, 59));
        webinar.setExpiryTime(expiryTime);
        System.out.println("DEBUG: Set expiry time to: " + webinar.getExpiryTime());

        // 3. Save Webinar
        Webinar savedWebinar = webinarRepository.save(webinar);

        // 4. Generate Form Link
        String formLink = frontendUrl + "?webinarId=" + savedWebinar.getId();

        // 5. Return Response
        return ResponseEntity.ok(Map.of(
                "webinarId", savedWebinar.getId(),
                "formLink", formLink
        ));
    }

    @GetMapping
    @Operation(summary = "List all webinars")
    public ResponseEntity<List<Map<String, Object>>> getAllWebinars() {
        List<Webinar> webinars = webinarRepository.findAll();
        List<Map<String, Object>> response = webinars.stream().map(webinar -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", webinar.getId());
            map.put("webinarTitle", webinar.getTitle());
            map.put("eventDate", webinar.getEventDate());
            map.put("startTime", webinar.getStartTime());
            map.put("endTime", webinar.getEndTime());
            map.put("expiryTime", webinar.getExpiryTime());
            map.put("createdAt", webinar.getCreatedAt());
            map.put("formLink", frontendUrl + "?webinarId=" + webinar.getId());
            return map;
        }).collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get webinar details by ID")
    public ResponseEntity<?> getWebinarById(@PathVariable Long id) {
        Webinar webinar = webinarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webinar not found with id: " + id));
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", webinar.getId());
        response.put("webinarTitle", webinar.getTitle());
        response.put("eventDate", webinar.getEventDate());
        response.put("startTime", webinar.getStartTime());
        response.put("endTime", webinar.getEndTime());
        response.put("expiryTime", webinar.getExpiryTime());
        response.put("createdAt", webinar.getCreatedAt());
        response.put("formLink", frontendUrl + "?webinarId=" + webinar.getId());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/registrations")
    @Operation(summary = "Get all registrations for a webinar")
    public ResponseEntity<?> getWebinarRegistrations(@PathVariable Long id) {
        Webinar webinar = webinarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webinar not found with id: " + id));
        List<Registration> registrations = registrationRepository.findByWebinarId(id);
        return ResponseEntity.ok(registrations);
    }
}
