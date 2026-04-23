package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.LeadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LeadTaskRepository extends JpaRepository<LeadTask, Long> {

    List<LeadTask> findByLeadId(Long leadId);

    List<LeadTask> findByLeadIdAndDueDateBetween(Long leadId, LocalDateTime start, LocalDateTime end);

    List<LeadTask> findByStatusAndDueDateBefore(LeadTask.TaskStatus status, LocalDateTime now);

    List<LeadTask> findByLeadAssignedToIn(java.util.Collection<com.lms.www.leadmanagement.entity.User> users);

    List<LeadTask> findByLeadAssignedToInAndDueDateBetween(java.util.Collection<com.lms.www.leadmanagement.entity.User> users, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM LeadTask t WHERE (t.lead.assignedTo.id IN :userIds OR t.lead.createdBy.id IN :userIds) AND (cast(:start as timestamp) IS NULL OR t.dueDate >= :start) AND (cast(:end as timestamp) IS NULL OR t.dueDate <= :end)")
    List<LeadTask> findFilteredByUserIds(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM LeadTask t WHERE (t.lead.assignedTo.id IN :userIds OR t.lead.createdBy.id IN :userIds) AND t.dueDate >= :start AND t.dueDate <= :end")
    long countFollowups(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM LeadTask t WHERE (t.lead.assignedTo.id IN :userIds OR t.lead.createdBy.id IN :userIds) AND t.status = com.lms.www.leadmanagement.entity.LeadTask$TaskStatus.PENDING AND t.dueDate < :now")
    long countPendingTasks(@Param("userIds") java.util.Collection<Long> userIds, @Param("now") LocalDateTime now);

    boolean existsByLeadIdAndStatusAndDueDate(Long leadId, LeadTask.TaskStatus status, LocalDateTime dueDate);
}
