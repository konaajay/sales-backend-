package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.LeadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeadTaskRepository extends JpaRepository<LeadTask, Long> {

    List<LeadTask> findByLeadId(Long leadId);

    List<LeadTask> findByStatusIn(Collection<LeadTask.TaskStatus> statuses);

    List<LeadTask> findByStatusInAndDueDateBefore(Collection<LeadTask.TaskStatus> statuses, LocalDateTime now);

    List<LeadTask> findByLeadIdAndDueDateBetween(Long leadId, LocalDateTime start, LocalDateTime end);

    List<LeadTask> findByLeadIdAndDueDate(Long leadId, LocalDateTime dueDate);

    List<LeadTask> findByStatusAndDueDateBefore(LeadTask.TaskStatus status, LocalDateTime now);

    List<LeadTask> findByLeadAssignedToIn(java.util.Collection<com.lms.www.leadmanagement.entity.User> users);

    List<LeadTask> findByLeadAssignedToInAndDueDateBetween(java.util.Collection<com.lms.www.leadmanagement.entity.User> users, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM LeadTask t LEFT JOIN t.assignedTo a LEFT JOIN t.createdBy c WHERE (a.id IN :userIds OR (a IS NULL AND c.id IN :userIds)) AND (:start IS NULL OR t.dueDate >= :start) AND (:end IS NULL OR t.dueDate <= :end)")
    List<LeadTask> findFilteredByUserIds(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    // -------------------------------
    // TODAY FOLLOW-UPS (STRICT)
    // -------------------------------
    @Query("SELECT COUNT(t) FROM LeadTask t WHERE " +
            "(t.assignedTo.id IN :userIds OR (t.assignedTo IS NULL AND t.createdBy.id IN :userIds)) " +
            "AND t.status NOT IN (com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED, com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED) " +
            "AND (:start IS NULL OR t.dueDate >= :start) AND (:end IS NULL OR t.dueDate <= :end)")
    long countFollowups(@Param("userIds") Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM LeadTask t WHERE t.status NOT IN (com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED, com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED) " +
            "AND (:start IS NULL OR t.dueDate >= :start) AND (:end IS NULL OR t.dueDate <= :end)")
    long countGlobalFollowups(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // -------------------------------
    // PENDING / OVERDUE (STRICT)
    // -------------------------------
    @Query("SELECT COUNT(t) FROM LeadTask t WHERE " +
            "(t.assignedTo.id IN :userIds OR (t.assignedTo IS NULL AND t.createdBy.id IN :userIds)) " +
            "AND t.status NOT IN (com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED, com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED) " +
            "AND (:now IS NULL OR t.dueDate < :now)")
    long countPendingTasks(@Param("userIds") Collection<Long> userIds, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(t) FROM LeadTask t WHERE t.status NOT IN (com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED, com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED) " +
            "AND (:now IS NULL OR t.dueDate < :now)")
    long countGlobalPendingTasks(@Param("now") LocalDateTime now);

    // -------------------------------
    // COMPLETED
    // -------------------------------
    @Query("SELECT COUNT(t) FROM LeadTask t WHERE t.status = com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED AND t.updatedAt >= :start AND t.updatedAt <= :end")
    long countGlobalCompletedToday(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM LeadTask t WHERE " +
            "(t.assignedTo.id IN :userIds OR (t.assignedTo IS NULL AND t.createdBy.id IN :userIds)) " +
            "AND t.status = com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED AND t.updatedAt >= :start AND t.updatedAt <= :end")
    long countCompletedToday(@Param("userIds") Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // -------------------------------
    // TYPE-BASED (SAFE)
    // -------------------------------
    @Query("SELECT COUNT(t) FROM LeadTask t WHERE " +
            "(t.assignedTo.id IN :userIds OR (t.assignedTo IS NULL AND t.createdBy.id IN :userIds)) " +
            "AND t.status NOT IN (com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED, com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED) " +
            "AND t.taskType = :type " +
            "AND (:start IS NULL OR t.dueDate >= :start) AND (:end IS NULL OR t.dueDate <= :end)")
    long countFollowupsByType(@Param("userIds") java.util.Collection<Long> userIds, @Param("type") String type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM LeadTask t WHERE t.status NOT IN (com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.COMPLETED, com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED) " +
            "AND t.taskType = :type " +
            "AND (:start IS NULL OR t.dueDate >= :start) AND (:end IS NULL OR t.dueDate <= :end)")
    long countGlobalFollowupsByType(@Param("type") String type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // -------------------------------
    // DUPLICATE PREVENTION & CLEANUP
    // -------------------------------
    boolean existsByLeadIdAndStatusAndDueDate(Long leadId, LeadTask.TaskStatus status, LocalDateTime dueDate);

    @Modifying
    @Query("UPDATE LeadTask t SET t.status = com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.CANCELLED WHERE t.lead.id = :leadId AND t.status = com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.PENDING")
    void cancelAllPendingByLeadId(@Param("leadId") Long leadId);
}
