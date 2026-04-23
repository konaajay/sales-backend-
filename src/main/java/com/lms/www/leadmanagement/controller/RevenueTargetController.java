package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.entity.RevenueTarget;
import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.repository.RevenueTargetRepository;
import com.lms.www.leadmanagement.repository.UserRepository;
import com.lms.www.leadmanagement.service.ManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/targets")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class RevenueTargetController {

    @Autowired
    private RevenueTargetRepository targetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ManagerService managerService;

    @PostMapping("/set")
    public ResponseEntity<?> setTarget(@RequestBody Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("userId") || !payload.containsKey("amount")) {
            return ResponseEntity.badRequest().body("Missing required target parameters (userId, amount)");
        }
        
        try {
            Long userId = Long.valueOf(payload.get("userId").toString());
            java.math.BigDecimal amount = new java.math.BigDecimal(payload.get("amount").toString());
            Integer month = payload.containsKey("month") ? Integer.valueOf(payload.get("month").toString()) : java.time.LocalDate.now().getMonthValue();
            Integer year = payload.containsKey("year") ? Integer.valueOf(payload.get("year").toString()) : java.time.LocalDate.now().getYear();

            User targetUser = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
            
            RevenueTarget target = targetRepository.findByUserIdAndMonthAndYear(userId, month, year)
                    .orElse(RevenueTarget.builder().user(targetUser).month(month).year(year).build());
            
            target.setTargetAmount(amount);
            
            // Also update the User entity's default monthlyTarget field
            targetUser.setMonthlyTarget(amount);
            userRepository.save(targetUser);
            
            return ResponseEntity.ok(targetRepository.save(target));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Target calibration failure: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllTargets(@RequestParam Integer month, @RequestParam Integer year) {
        return ResponseEntity.ok(targetRepository.findByMonthAndYear(month, year));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getTargetForUser(@PathVariable Long userId, @RequestParam Integer month, @RequestParam Integer year) {
        return ResponseEntity.ok(targetRepository.findByUserIdAndMonthAndYear(userId, month, year));
    }
}
