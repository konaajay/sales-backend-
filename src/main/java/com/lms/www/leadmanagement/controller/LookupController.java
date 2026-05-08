package com.lms.www.leadmanagement.controller;

import com.lms.www.leadmanagement.dto.ApiResponse;
import com.lms.www.leadmanagement.entity.PipelineStage;
import com.lms.www.leadmanagement.repository.PipelineStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lookup")
@RequiredArgsConstructor
public class LookupController {

    private final PipelineStageRepository pipelineStageRepository;

    @GetMapping("/pipeline-stages")
    public ResponseEntity<ApiResponse<List<PipelineStage>>> getActiveStages() {
        return ResponseEntity.ok(ApiResponse.success(pipelineStageRepository.findByActiveTrueOrderByOrderIndexAsc()));
    }
}
