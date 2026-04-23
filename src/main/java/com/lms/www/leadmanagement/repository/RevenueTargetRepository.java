package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.RevenueTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface RevenueTargetRepository extends JpaRepository<RevenueTarget, Long> {
    Optional<RevenueTarget> findByUserIdAndMonthAndYear(Long userId, Integer month, Integer year);
    List<RevenueTarget> findByUserIdInAndMonthAndYear(java.util.Collection<Long> userIds, Integer month, Integer year);
    List<RevenueTarget> findByMonthAndYear(Integer month, Integer year);
}
