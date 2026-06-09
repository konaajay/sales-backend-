package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.entity.College;
import com.lms.www.leadmanagement.repository.CollegeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/colleges")
@Tag(name = "College Management", description = "APIs for managing colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeRepository collegeRepository;

    // ─── GET ALL (with optional search + status filter + pagination) ───────────
    @GetMapping
    @Operation(summary = "Get all colleges with optional search, status filter and pagination")
    public ResponseEntity<?> getAllColleges(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) Boolean status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<College> all = collegeRepository.findAll(Sort.by(Sort.Direction.ASC, "collegeName"));

        // Filter by search
        if (search != null && !search.isBlank()) {
            String term = search.toLowerCase();
            all = all.stream()
                    .filter(c -> c.getCollegeName().toLowerCase().contains(term))
                    .collect(Collectors.toList());
        }

        // Filter by status
        if (status != null) {
            all = all.stream()
                    .filter(c -> c.getStatus().equals(status))
                    .collect(Collectors.toList());
        }

        // Manual pagination
        int total = all.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        List<College> pageContent = (start >= total) ? List.of() : all.subList(start, end);

        Page<College> result = new PageImpl<>(pageContent, PageRequest.of(page, size), total);

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", page,
                "size", size
        ));
    }

    // ─── GET ACTIVE (for dropdowns in forms) ──────────────────────────────────
    @GetMapping("/active")
    @Operation(summary = "Get all active colleges (for dropdowns)")
    public ResponseEntity<List<College>> getActiveColleges() {
        List<College> active = collegeRepository.findByStatusTrue();
        active.sort((a, b) -> a.getCollegeName().compareToIgnoreCase(b.getCollegeName()));
        return ResponseEntity.ok(active);
    }

    // ─── GET BY ID ────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get a single college by ID")
    public ResponseEntity<?> getCollegeById(@PathVariable Long id) {
        return collegeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new college")
    public ResponseEntity<?> createCollege(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("collegeName");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "College name is required"));
        }
        if (collegeRepository.existsByCollegeNameIgnoreCase(name.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "College already exists"));
        }

        College college = new College();
        college.setCollegeName(name.trim());
        college.setStatus(body.get("status") == null || Boolean.TRUE.equals(body.get("status")));

        College saved = collegeRepository.save(college);
        return ResponseEntity.ok(saved);
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update a college")
    public ResponseEntity<?> updateCollege(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return collegeRepository.findById(id).map(college -> {
            String name = (String) body.get("collegeName");
            if (name != null && !name.isBlank()) {
                // Check uniqueness excluding self
                boolean duplicate = collegeRepository.findAll().stream()
                        .anyMatch(c -> !c.getId().equals(id)
                                && c.getCollegeName().equalsIgnoreCase(name.trim()));
                if (duplicate) {
                    return ResponseEntity.badRequest()
                            .<Object>body(Map.of("error", "Another college with this name already exists"));
                }
                college.setCollegeName(name.trim());
            }
            if (body.get("status") != null) {
                college.setStatus(Boolean.TRUE.equals(body.get("status")));
            }
            return ResponseEntity.<Object>ok(collegeRepository.save(college));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a college")
    public ResponseEntity<?> deleteCollege(@PathVariable Long id) {
        if (!collegeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        collegeRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "College deleted successfully"));
    }

    // ─── TOGGLE STATUS ────────────────────────────────────────────────────────
    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle the active/inactive status of a college")
    public ResponseEntity<?> toggleStatus(@PathVariable Long id) {
        return collegeRepository.findById(id).map(college -> {
            college.setStatus(!college.getStatus());
            College updated = collegeRepository.save(college);
            return ResponseEntity.<Object>ok(Map.of(
                    "id", updated.getId(),
                    "collegeName", updated.getCollegeName(),
                    "status", updated.getStatus(),
                    "message", updated.getStatus() ? "College activated" : "College deactivated"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
