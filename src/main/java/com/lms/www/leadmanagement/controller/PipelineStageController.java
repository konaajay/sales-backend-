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
    public ResponseEntity<com.lms.www.leadmanagement.dto.ApiResponse<List<PipelineStage>>> getAllStages() {
        return ResponseEntity.ok(com.lms.www.leadmanagement.dto.ApiResponse.success(pipelineStageRepository.findAllByOrderByOrderIndexAsc()));
    }

    @GetMapping("/active")
    public ResponseEntity<com.lms.www.leadmanagement.dto.ApiResponse<List<PipelineStage>>> getActiveStages() {
        return ResponseEntity.ok(com.lms.www.leadmanagement.dto.ApiResponse.success(pipelineStageRepository.findByActiveTrueOrderByOrderIndexAsc()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<com.lms.www.leadmanagement.dto.ApiResponse<?>> createStage(@RequestBody PipelineStage stage) {
        System.out.println(">>> Request: CREATE PipelineStage - Label: " + stage.getLabel() + ", Value: " + stage.getStatusValue());
        
        if (stage.getStatusValue() == null || stage.getStatusValue().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(com.lms.www.leadmanagement.dto.ApiResponse.error("Status value is required"));
        }

        // Standardize status value if it matches a known system status
        String statusToUse = stage.getStatusValue();
        com.lms.www.leadmanagement.entity.LeadStatus matchedStatus = com.lms.www.leadmanagement.entity.LeadStatus.fromString(statusToUse);
        
        if (matchedStatus != null) {
            statusToUse = matchedStatus.name();
            stage.setStatusValue(statusToUse);
        }

        if (pipelineStageRepository.existsByStatusValue(statusToUse)) {
            return ResponseEntity.badRequest().body(com.lms.www.leadmanagement.dto.ApiResponse.error("Status value '" + statusToUse + "' already exists"));
        }
        
        return ResponseEntity.ok(com.lms.www.leadmanagement.dto.ApiResponse.success(pipelineStageRepository.save(stage)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<com.lms.www.leadmanagement.dto.ApiResponse<PipelineStage>> updateStage(@PathVariable Long id, @RequestBody PipelineStage stageDetails) {
        System.out.println(">>> Request: UPDATE PipelineStage ID: " + id + " - New Label: " + stageDetails.getLabel());
        PipelineStage stage = pipelineStageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
        
        if (stageDetails.getStatusValue() != null && !stageDetails.getStatusValue().equals(stage.getStatusValue())) {
            String statusToUse = stageDetails.getStatusValue();
            try {
                statusToUse = com.lms.www.leadmanagement.entity.LeadStatus.fromString(statusToUse).name();
            } catch (Exception e) {}

            if (pipelineStageRepository.existsByStatusValue(statusToUse)) {
                return ResponseEntity.badRequest().body(com.lms.www.leadmanagement.dto.ApiResponse.error("Status value '" + statusToUse + "' already exists"));
            }
            stage.setStatusValue(statusToUse);
        }

        stage.setLabel(stageDetails.getLabel());
        stage.setColor(stageDetails.getColor());
        stage.setAnalyticBucket(stageDetails.getAnalyticBucket());
        stage.setOrderIndex(stageDetails.getOrderIndex());
        stage.setActive(stageDetails.isActive());
        
        // Smart Config
        stage.setRequireNote(stageDetails.isRequireNote());
        stage.setRequireDate(stageDetails.isRequireDate());
        stage.setCreateTask(stageDetails.isCreateTask());
        
        return ResponseEntity.ok(com.lms.www.leadmanagement.dto.ApiResponse.success(pipelineStageRepository.save(stage)));
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<com.lms.www.leadmanagement.dto.ApiResponse<Void>> deleteStage(@PathVariable Long id) {
        pipelineStageRepository.deleteById(id);
        return ResponseEntity.ok(com.lms.www.leadmanagement.dto.ApiResponse.success(null));
    }
}
