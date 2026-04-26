package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.entity.PipelineStage;
import com.lms.www.leadmanagement.repository.PipelineStageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/pipeline-stages")
public class PipelineStageController {

    @Autowired
    private PipelineStageRepository pipelineStageRepository;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_LEADS', 'ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<List<PipelineStage>> getAllStages() {
        return ResponseEntity.ok(pipelineStageRepository.findAllByOrderByOrderIndexAsc());
    }

    @GetMapping("/active")
    public ResponseEntity<List<PipelineStage>> getActiveStages() {
        return ResponseEntity.ok(pipelineStageRepository.findByActiveTrueOrderByOrderIndexAsc());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<?> createStage(@RequestBody PipelineStage stage) {
        if (pipelineStageRepository.existsByStatusValue(stage.getStatusValue())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status value already exists"));
        }
        return ResponseEntity.ok(pipelineStageRepository.save(stage));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<PipelineStage> updateStage(@PathVariable Long id, @RequestBody PipelineStage stageDetails) {
        PipelineStage stage = pipelineStageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
        
        stage.setLabel(stageDetails.getLabel());
        stage.setColor(stageDetails.getColor());
        stage.setAnalyticBucket(stageDetails.getAnalyticBucket());
        stage.setOrderIndex(stageDetails.getOrderIndex());
        stage.setActive(stageDetails.isActive());
        
        // Smart Config
        stage.setRequireNote(stageDetails.isRequireNote());
        stage.setRequireDate(stageDetails.isRequireDate());
        stage.setCreateTask(stageDetails.isCreateTask());
        
        return ResponseEntity.ok(pipelineStageRepository.save(stage));
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<?> deleteStage(@PathVariable Long id) {
        pipelineStageRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
