package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.WfhRequest;
import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WfhRequestRepository extends JpaRepository<WfhRequest, Long> {
    
    List<WfhRequest> findByStatus(String status);
    
    @Query("SELECT w FROM WfhRequest w WHERE w.user.id = :userId AND w.status = 'PENDING'")
    Optional<WfhRequest> findPendingByUserId(@Param("userId") Long userId);

    @Query("SELECT w FROM WfhRequest w WHERE w.user.id = :userId AND w.status = 'APPROVED' ORDER BY w.respondedAt DESC")
    List<WfhRequest> findLatestApprovedByUserId(@Param("userId") Long userId);
}
