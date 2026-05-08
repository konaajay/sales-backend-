package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.dto.DashboardProjection;
import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    @Override
    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "updatedBy"})
    Optional<Lead> findById(Long id);

    List<Lead> findByEmail(String email);
    List<Lead> findByMobile(String mobile);
    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);

    List<Lead> findByAssignedTo(User assignedTo);
    List<Lead> findByStatusIn(Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses);

    long countByStatus(com.lms.www.leadmanagement.entity.LeadStatus status);
    @Query("SELECT count(l) FROM Lead l WHERE l.status IN :statuses")
    long countByStatusIn(@Param("statuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses);
    @Query("SELECT count(l) FROM Lead l WHERE l.createdAt BETWEEN :start AND :end AND l.status IN :statuses")
    long countByCreatedAtBetweenAndStatusIn(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("statuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses);
    long countByFollowUpDateBetween(LocalDateTime start, LocalDateTime end);
    long countByFollowUpDateBefore(LocalDateTime now);
    @Modifying
    @Query("UPDATE Lead l SET l.assignedTo = NULL WHERE l.assignedTo.id = :userId AND l.status NOT IN (com.lms.www.leadmanagement.entity.LeadStatus.PAID, com.lms.www.leadmanagement.entity.LeadStatus.SUCCESS, com.lms.www.leadmanagement.entity.LeadStatus.EMI, com.lms.www.leadmanagement.entity.LeadStatus.CONVERTED)")
    void unassignNonFinalizedLeads(@Param("userId") Long userId);

    @Query("SELECT l FROM Lead l WHERE l.assignedTo IS NULL OR l.assignedTo.active = false")
    List<Lead> findByAssignedToIsNull();

    List<Lead> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByAssignedToIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
    long countByAssignedToIdAndCreatedAtBetweenAndStatusIn(Long userId, LocalDateTime start, LocalDateTime end, Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses);

    @Query("SELECT count(l) FROM Lead l WHERE l.assignedTo.id IN :userIds AND l.status IN :statuses AND l.createdAt BETWEEN :start AND :end")
    long countByAssignedToIdInAndStatusInAndCreatedAtBetween(
            @Param("userIds") Collection<Long> userIds, 
            @Param("statuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end);

    List<Lead> findByCreatedAtBetweenAndAssignedToIn(
            LocalDateTime start,
            LocalDateTime end,
            Collection<User> users);

    Page<Lead> findByAssignedToIn(
            Collection<User> users,
            Pageable pageable);

    @Query("SELECT l FROM Lead l WHERE l.assignedTo IN :users")
    List<Lead> findByAssignedToIn(@Param("users") Collection<User> users);

    @Query("SELECT l FROM Lead l WHERE l.assignedTo IN :users OR l.createdBy = :creator")
    List<Lead> findByAssignedToInOrCreatedBy(@Param("users") Collection<User> users, @Param("creator") User creator);

    @Query("SELECT l.id FROM Lead l LEFT JOIN l.assignedTo a " +
            "WHERE (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds))")
    List<Long> findLeadIdsByUserIds(@Param("userIds") Collection<Long> userIds);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "updatedBy"})
    @Query("SELECT l FROM Lead l LEFT JOIN FETCH l.assignedTo LEFT JOIN FETCH l.createdBy " +
            "WHERE l.assignedTo IN :users OR l.createdBy IN :creators")
    List<Lead> findListByAssignedToInOrCreatedByIn(@Param("users") java.util.Collection<User> users, @Param("creators") java.util.Collection<User> creators);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "updatedBy"})
    @Query("SELECT l FROM Lead l LEFT JOIN l.assignedTo a LEFT JOIN l.createdBy c " +
            "WHERE ((:isUnassigned = true AND a IS NULL AND c.id IN :userIds) OR " +
            "      (:isUnassigned = false AND (a.id IN :userIds OR (a IS NULL AND c.id IN :userIds)))) " +
            "AND (:start IS NULL OR l.createdAt >= :start) " +
            "AND (:end IS NULL OR l.createdAt <= :end)")
    Page<Lead> findHierarchicalLeads(@Param("userIds") Collection<Long> userIds, 
                                    @Param("start") LocalDateTime start, 
                                    @Param("end") LocalDateTime end, 
                                    @Param("isUnassigned") boolean isUnassigned,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "updatedBy"})
    @Query("SELECT l FROM Lead l WHERE l.assignedTo IN :users OR l.createdBy IN :creators")
    Page<Lead> findByAssignedToInOrCreatedByIn(@Param("users") Collection<User> users, @Param("creators") Collection<User> creators, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy", "updatedBy"})
    @Query("SELECT l FROM Lead l WHERE (l.assignedTo IN :users OR l.createdBy IN :creators) AND l.status IN :statuses")
    Page<Lead> findByAssignedToInOrCreatedByInAndStatusIn(@Param("users") Collection<User> users, @Param("creators") Collection<User> creators, @Param("statuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses, Pageable pageable);

    List<Lead> findByAssignedToOrCreatedBy(User assignedTo, User createdBy);

    @Query("SELECT count(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds))")
    long countTotalRegistry(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT count(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.createdAt BETWEEN :start AND :end")
    long countSquadLeads(@Param("userIds") Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT count(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.status IN :statuses AND l.updatedAt BETWEEN :start AND :end")
    long countSquadConversionsInPeriod(@Param("userIds") Collection<Long> userIds, @Param("statuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT count(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.status IN :statuses AND l.createdAt BETWEEN :start AND :end")
    long countSquadLeadsByStatus(@Param("userIds") Collection<Long> userIds, @Param("statuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> statuses, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT u.id as userId, u.name as username, u.role.name as role, " +
            "(SELECT count(l) FROM Lead l WHERE (l.assignedTo.id = u.id OR (l.assignedTo IS NULL AND l.createdBy.id = u.id))) as totalLeads, " +
            "(SELECT count(l) FROM Lead l WHERE (l.assignedTo.id = u.id OR (l.assignedTo IS NULL AND l.createdBy.id = u.id)) AND l.status IN :successStatuses AND l.updatedAt BETWEEN :start AND :end) as convertedCount, " +
            "(SELECT count(l) FROM Lead l WHERE (l.assignedTo.id = u.id OR (l.assignedTo IS NULL AND l.createdBy.id = u.id)) AND l.status IN :lostStatuses AND l.updatedAt BETWEEN :start AND :end) as lostCount " +
            "FROM User u " +
            "WHERE u IN :users")
    List<Map<String, Object>> getMemberPerformanceStats(@Param("users") Collection<User> users, 
                                                       @Param("successStatuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> successStatuses,
                                                       @Param("lostStatuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> lostStatuses,
                                                       @Param("start") LocalDateTime start, 
                                                       @Param("end") LocalDateTime end);

    @Query("SELECT l.assignedTo.id as userId, u.name as username, count(l) as count " +
            "FROM Lead l JOIN l.assignedTo u WHERE l.assignedTo IN :users AND l.createdAt BETWEEN :start AND :end " +
            "GROUP BY l.assignedTo.id, u.name")
    List<Map<String, Object>> getLeadCountsByUserIds(@Param("users") Collection<User> users, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT l.status as status, count(l) as count FROM Lead l WHERE l.assignedTo.id = :userId GROUP BY l.status")
    List<Map<String, Object>> getStatusCountsByUserId(@Param("userId") Long userId);

    @Query("SELECT new map(" +
            "count(l) as total, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.NEW then 1 else 0 end) as newCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.CONTACTED then 1 else 0 end) as contactedCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.UNDER_REVIEW) then 1 else 0 end) as interestedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.FOLLOW_UP then 1 else 0 end) as followUpCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.CONVERTED, com.lms.www.leadmanagement.entity.LeadStatus.PAID, com.lms.www.leadmanagement.entity.LeadStatus.EMI, com.lms.www.leadmanagement.entity.LeadStatus.SUCCESS) AND l.updatedAt BETWEEN :start AND :end then 1 else 0 end) as convertedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.REJECTED then 1 else 0 end) as rejectedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.REFUND then 1 else 0 end) as refundCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.LOST, com.lms.www.leadmanagement.entity.LeadStatus.NOT_INTERESTED) AND l.updatedAt BETWEEN :start AND :end then 1 else 0 end) as lostCount) " +
            "FROM Lead l LEFT JOIN l.assignedTo a " +
            "WHERE (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds))")
    Map<String, Long> getSummaryStats(
            @Param("userIds") Collection<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
            "count(l) as total, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.NEW then 1 else 0 end) as newCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.CONTACTED then 1 else 0 end) as contactedCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.UNDER_REVIEW) then 1 else 0 end) as interestedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.FOLLOW_UP then 1 else 0 end) as followUpCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.CONVERTED, com.lms.www.leadmanagement.entity.LeadStatus.PAID, com.lms.www.leadmanagement.entity.LeadStatus.EMI, com.lms.www.leadmanagement.entity.LeadStatus.SUCCESS) then 1 else 0 end) as convertedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.REJECTED then 1 else 0 end) as rejectedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.REFUND then 1 else 0 end) as refundCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.LOST, com.lms.www.leadmanagement.entity.LeadStatus.NOT_INTERESTED) then 1 else 0 end) as lostCount) " +
            "FROM Lead l " +
            "WHERE l.createdAt BETWEEN :start AND :end")
    Map<String, Long> getGlobalSummaryStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
            "count(l) as total, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.NEW then 1 else 0 end) as newCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.CONTACTED then 1 else 0 end) as contactedCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.UNDER_REVIEW) then 1 else 0 end) as interestedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.FOLLOW_UP then 1 else 0 end) as followUpCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.CONVERTED, com.lms.www.leadmanagement.entity.LeadStatus.PAID, com.lms.www.leadmanagement.entity.LeadStatus.EMI, com.lms.www.leadmanagement.entity.LeadStatus.SUCCESS) then 1 else 0 end) as convertedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.REJECTED then 1 else 0 end) as rejectedCount, " +
            "sum(case when l.status = com.lms.www.leadmanagement.entity.LeadStatus.REFUND then 1 else 0 end) as refundCount, " +
            "sum(case when l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.LOST, com.lms.www.leadmanagement.entity.LeadStatus.NOT_INTERESTED) then 1 else 0 end) as lostCount) " +
            "FROM Lead l " +
            "WHERE l.assignedTo.id = :userId AND l.createdAt BETWEEN :start AND :end")
    Map<String, Long> getUserSpecificSummaryStats(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.followUpDate BETWEEN :start AND :end")
    long countLeadsByFollowUpDate(@Param("userIds") Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.followUpDate < :now AND l.status NOT IN (com.lms.www.leadmanagement.entity.LeadStatus.CONVERTED, com.lms.www.leadmanagement.entity.LeadStatus.PAID, com.lms.www.leadmanagement.entity.LeadStatus.EMI, com.lms.www.leadmanagement.entity.LeadStatus.SUCCESS, com.lms.www.leadmanagement.entity.LeadStatus.LOST, com.lms.www.leadmanagement.entity.LeadStatus.NOT_INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.CLOSED, com.lms.www.leadmanagement.entity.LeadStatus.COMPLETED)")
    long countOverdueLeads(@Param("userIds") Collection<Long> userIds, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(l) FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.UNDER_REVIEW) AND l.followUpDate <= :now")
    long countHighPriorityLeads(@Param("userIds") Collection<Long> userIds, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.followUpDate BETWEEN :start AND :end")
    long countGlobalLeadsByFollowUpDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.followUpDate < :now AND l.status NOT IN (com.lms.www.leadmanagement.entity.LeadStatus.CONVERTED, com.lms.www.leadmanagement.entity.LeadStatus.PAID, com.lms.www.leadmanagement.entity.LeadStatus.EMI, com.lms.www.leadmanagement.entity.LeadStatus.SUCCESS, com.lms.www.leadmanagement.entity.LeadStatus.LOST, com.lms.www.leadmanagement.entity.LeadStatus.NOT_INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.CLOSED, com.lms.www.leadmanagement.entity.LeadStatus.COMPLETED)")
    long countGlobalOverdueLeads(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.status IN (com.lms.www.leadmanagement.entity.LeadStatus.INTERESTED, com.lms.www.leadmanagement.entity.LeadStatus.UNDER_REVIEW) AND l.followUpDate <= :now")
    long countGlobalHighPriorityLeads(@Param("now") LocalDateTime now);

    @Query("SELECT new map(FUNCTION('DATE', l.createdAt) as date, count(l) as count) " +
            "FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) AND l.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY FUNCTION('DATE', l.createdAt)")
    List<Map<String, Object>> getDailyLeadTrendByIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.createdAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY FUNCTION('DATE', l.createdAt)")
    List<Map<String, Object>> getGlobalDailyLeadTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.updatedAt) as date, count(l) as count) " +
            "FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.status IN :successStatuses AND l.updatedAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.updatedAt) ORDER BY FUNCTION('DATE', l.updatedAt)")
    List<Map<String, Object>> getDailyConvertedTrendByIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("successStatuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> successStatuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.updatedAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.status IN :successStatuses AND l.updatedAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.updatedAt) ORDER BY FUNCTION('DATE', l.updatedAt)")
    List<Map<String, Object>> getGlobalDailyConvertedTrend(
            @Param("successStatuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> successStatuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.updatedAt) as date, count(l) as count) " +
            "FROM Lead l WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND l.status IN :lostStatuses AND l.updatedAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.updatedAt) ORDER BY FUNCTION('DATE', l.updatedAt)")
    List<Map<String, Object>> getDailyLostTrendByIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("lostStatuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> lostStatuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.updatedAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.status IN :lostStatuses AND l.updatedAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.updatedAt) ORDER BY FUNCTION('DATE', l.updatedAt)")
    List<Map<String, Object>> getGlobalDailyLostTrend(
            @Param("lostStatuses") Collection<com.lms.www.leadmanagement.entity.LeadStatus> lostStatuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);


    @Query("SELECT l.status as status, COUNT(l) as count FROM Lead l " +
           "LEFT JOIN l.assignedTo a " +
           "WHERE (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) " +
           "AND l.createdAt BETWEEN :start AND :end GROUP BY l.status")
    List<DashboardProjection> countByStatusForUsers(
            @Param("userIds") Collection<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT l.status as status, COUNT(l) as count FROM Lead l " +
           "WHERE l.createdAt BETWEEN :start AND :end GROUP BY l.status")
    List<DashboardProjection> countByStatusGlobal(
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end);

    @Query("SELECT l FROM Lead l WHERE l.assignedTo.id = :userId " +
           "AND l.followUpRequired = true " +
           "AND l.followUpDate BETWEEN :start AND :end")
    List<Lead> findUpcomingFollowUps(
            @Param("userId") Long userId, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end);
}
