package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.RevenueTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface RevenueTargetRepository extends JpaRepository<RevenueTarget, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT t FROM RevenueTarget t WHERE t.user.id = :userId AND t.month = :month AND t.year = :year AND t.type = 'ASSIGNED' AND t.assignedBy != :userId ORDER BY t.createdAt DESC")
    List<RevenueTarget> findAssignedBudget(@org.springframework.data.repository.query.Param("userId") Long userId, 
                                               @org.springframework.data.repository.query.Param("month") Integer month, 
                                               @org.springframework.data.repository.query.Param("year") Integer year);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM RevenueTarget t WHERE t.user.id = :userId AND t.month = :month AND t.year = :year AND t.type = 'ASSIGNED' ORDER BY t.createdAt DESC")
    List<RevenueTarget> findAssignedTarget(@org.springframework.data.repository.query.Param("userId") Long userId, 
                                              @org.springframework.data.repository.query.Param("month") Integer month, 
                                              @org.springframework.data.repository.query.Param("year") Integer year);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(t.targetAmount) FROM RevenueTarget t WHERE t.id IN (SELECT MAX(t2.id) FROM RevenueTarget t2 WHERE t2.assignedBy = :userId AND t2.month = :month AND t2.year = :year GROUP BY t2.user.id)")
    java.math.BigDecimal getDistributedTotal(@org.springframework.data.repository.query.Param("userId") Long userId, 
                                           @org.springframework.data.repository.query.Param("month") Integer month, 
                                           @org.springframework.data.repository.query.Param("year") Integer year);

    Optional<RevenueTarget> findFirstByUserIdAndMonthAndYearOrderByIdDesc(Long userId, Integer month, Integer year);
    List<RevenueTarget> findAllByUserIdAndMonthAndYearOrderByIdDesc(Long userId, Integer month, Integer year);
    List<RevenueTarget> findByUserIdInAndMonthAndYear(java.util.Collection<Long> userIds, Integer month, Integer year);
    List<RevenueTarget> findByMonthAndYear(Integer month, Integer year);
 
    @org.springframework.data.jpa.repository.Query("SELECT SUM(rt.targetAmount) FROM RevenueTarget rt WHERE rt.id IN (SELECT MAX(rt2.id) FROM RevenueTarget rt2 WHERE rt2.user.id IN :userIds AND rt2.month = :month AND rt2.year = :year GROUP BY rt2.user.id)")
    java.math.BigDecimal findTotalTargetForUsers(@org.springframework.data.repository.query.Param("userIds") java.util.Collection<Long> userIds, 
                                               @org.springframework.data.repository.query.Param("month") Integer month, 
                                               @org.springframework.data.repository.query.Param("year") Integer year);
 
    Optional<RevenueTarget> findTopByUserIdAndMonthAndYearAndTypeOrderByIdDesc(Long userId, Integer month, Integer year, com.lms.www.leadmanagement.entity.TargetType type);
    Optional<RevenueTarget> findTopByUserIdAndMonthAndYearAndTypeAndAssignedByOrderByIdDesc(Long userId, Integer month, Integer year, com.lms.www.leadmanagement.entity.TargetType type, Long assignedBy);
 
    @org.springframework.data.jpa.repository.Query("SELECT t FROM RevenueTarget t WHERE t.id IN (SELECT MAX(t2.id) FROM RevenueTarget t2 WHERE t2.assignedBy = :assignerId AND t2.month = :month AND t2.year = :year GROUP BY t2.user.id)")
    List<RevenueTarget> findAllByAssignedByAndMonthAndYear(@org.springframework.data.repository.query.Param("assignerId") Long assignerId, 
                                                            @org.springframework.data.repository.query.Param("month") Integer month, 
                                                            @org.springframework.data.repository.query.Param("year") Integer year);

    List<RevenueTarget> findAllByUserIdOrderByYearDescMonthDesc(Long userId);
    @org.springframework.data.jpa.repository.Query("SELECT t FROM RevenueTarget t WHERE t.id IN (SELECT MAX(t2.id) FROM RevenueTarget t2 WHERE t2.user.id = :userId GROUP BY t2.month, t2.year)")
    List<RevenueTarget> findAllByUserIdLatest(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM RevenueTarget t WHERE t.id IN (SELECT MAX(t2.id) FROM RevenueTarget t2 WHERE t2.user.id = :userId AND t2.type = :type GROUP BY t2.month, t2.year)")
    List<RevenueTarget> findAllByUserIdAndTypeLatest(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("type") com.lms.www.leadmanagement.entity.TargetType type);

}
