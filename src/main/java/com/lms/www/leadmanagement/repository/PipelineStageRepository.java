package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {
    List<PipelineStage> findAllByOrderByOrderIndexAsc();
    List<PipelineStage> findByActiveTrueOrderByOrderIndexAsc();
    boolean existsByStatusValue(String statusValue);
    Optional<PipelineStage> findByStatusValue(String statusValue);
    List<PipelineStage> findByAnalyticBucketIn(java.util.Collection<String> buckets);
}
