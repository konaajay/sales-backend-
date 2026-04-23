package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.Lead;
import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<Lead> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    boolean existsByEmail(String email);

    List<Lead> findByAssignedTo(User assignedTo);
    List<Lead> findByAssignedToOrCreatedBy(User assignedTo, User createdBy);
    long countByAssignedTo(User assignedTo);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByStatus(Lead.Status status);
    long countByStatusIn(Collection<Lead.Status> statuses);
    long countByCreatedAtBetweenAndStatusIn(LocalDateTime start, LocalDateTime end, Collection<Lead.Status> statuses);
    long countByFollowUpDateBetween(LocalDateTime start, LocalDateTime end);
    long countByFollowUpDateBefore(LocalDateTime now);
    List<Lead> findByAssignedToIsNull();

    List<Lead> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

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

    @Query("SELECT l FROM Lead l WHERE l.assignedTo IN :users OR l.createdBy IN :creators")
    List<Lead> findByAssignedToInOrCreatedByIn(@Param("users") Collection<User> users, @Param("creators") Collection<User> creators);

    Page<Lead> findByAssignedToInAndCreatedAtBetween(
            Collection<User> users,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);

    Optional<Lead> findByIdAndAssignedTo(Long id, User assignedTo);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.assignedTo IN :users AND l.status IN :statuses")
    long countByAssignedToInAndStatusIn(@Param("users") Collection<User> users, @Param("statuses") Collection<Lead.Status> statuses);

    boolean existsByEmailAndAssignedTo(String email, User assignedTo);

    boolean existsByMobileAndAssignedTo(String mobile, User assignedTo);

    @Query("SELECT new map(" +
            "count(l) as total, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.NEW) then 1 else 0 end), 0) as newCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.INTERESTED, com.lms.www.leadmanagement.entity.Lead$Status.UNDER_REVIEW) then 1 else 0 end), 0) as interestedCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.CONTACTED) then 1 else 0 end), 0) as contactedCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.FOLLOW_UP) then 1 else 0 end), 0) as followUpCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.CONVERTED, com.lms.www.leadmanagement.entity.Lead$Status.PAID, com.lms.www.leadmanagement.entity.Lead$Status.EMI, com.lms.www.leadmanagement.entity.Lead$Status.SUCCESS) then 1 else 0 end), 0) as convertedCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.LOST, com.lms.www.leadmanagement.entity.Lead$Status.NOT_INTERESTED, com.lms.www.leadmanagement.entity.Lead$Status.PAYMENT_FAILED) then 1 else 0 end), 0) as lostCount) " +
            "FROM Lead l WHERE l.assignedTo IN :users AND l.createdAt BETWEEN :start AND :end")
    Map<String, Long> getSummaryStats(
            @Param("users") Collection<User> users,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
            "count(l) as total, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.NEW) then 1 else 0 end), 0) as newCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.INTERESTED, com.lms.www.leadmanagement.entity.Lead$Status.UNDER_REVIEW) then 1 else 0 end), 0) as interestedCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.CONTACTED) then 1 else 0 end), 0) as contactedCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.FOLLOW_UP) then 1 else 0 end), 0) as followUpCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.CONVERTED, com.lms.www.leadmanagement.entity.Lead$Status.PAID, com.lms.www.leadmanagement.entity.Lead$Status.EMI, com.lms.www.leadmanagement.entity.Lead$Status.SUCCESS) then 1 else 0 end), 0) as convertedCount, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.LOST, com.lms.www.leadmanagement.entity.Lead$Status.NOT_INTERESTED, com.lms.www.leadmanagement.entity.Lead$Status.PAYMENT_FAILED) then 1 else 0 end), 0) as lostCount) " +
            "FROM Lead l WHERE l.createdAt BETWEEN :start AND :end")
    Map<String, Long> getGlobalSummaryStats(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.createdAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.assignedTo.id IN :userIds AND l.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY FUNCTION('DATE', l.createdAt)")
    List<Map<String, Object>> getDailyLeadTrend(
            @Param("userIds") Collection<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.createdAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY FUNCTION('DATE', l.createdAt)")
    List<Map<String, Object>> getGlobalDailyLeadTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.createdAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.assignedTo.id IN :userIds AND l.createdAt BETWEEN :start AND :end " +
            "AND (l.status = com.lms.www.leadmanagement.entity.Lead$Status.LOST OR l.status = com.lms.www.leadmanagement.entity.Lead$Status.NOT_INTERESTED) " +
            "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY FUNCTION('DATE', l.createdAt)")
    List<Map<String, Object>> getDailyLostTrend(
            @Param("userIds") Collection<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', l.createdAt) as date, count(l) as count) " +
            "FROM Lead l WHERE l.createdAt BETWEEN :start AND :end " +
            "AND (l.status = com.lms.www.leadmanagement.entity.Lead$Status.LOST OR l.status = com.lms.www.leadmanagement.entity.Lead$Status.NOT_INTERESTED OR l.status = com.lms.www.leadmanagement.entity.Lead$Status.PAYMENT_FAILED) " +
            "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY FUNCTION('DATE', l.createdAt)")
    List<Map<String, Object>> getGlobalDailyLostTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(" +
            "u.id as userId, " +
            "u.name as username, " +
            "u.role.name as role, " +
            "count(l) as totalLeads, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.INTERESTED, com.lms.www.leadmanagement.entity.Lead$Status.EMI, com.lms.www.leadmanagement.entity.Lead$Status.UNDER_REVIEW) then 1 else 0 end), 0) as interestedLeads, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.CONVERTED, com.lms.www.leadmanagement.entity.Lead$Status.PAID, com.lms.www.leadmanagement.entity.Lead$Status.SUCCESS, com.lms.www.leadmanagement.entity.Lead$Status.EMI) then 1 else 0 end), 0) as convertedLeads, " +
            "coalesce(sum(case when l.status IN (com.lms.www.leadmanagement.entity.Lead$Status.LOST, com.lms.www.leadmanagement.entity.Lead$Status.NOT_INTERESTED, com.lms.www.leadmanagement.entity.Lead$Status.PAYMENT_FAILED) then 1 else 0 end), 0) as lostLeads, " +
            "coalesce(sum(case when l.status = com.lms.www.leadmanagement.entity.Lead$Status.CONTACTED then 1 else 0 end), 0) as callsMade) " +
            "FROM User u LEFT JOIN Lead l ON l.assignedTo = u AND l.createdAt BETWEEN :start AND :end " +
            "WHERE u IN :users " +
            "GROUP BY u.id, u.name, u.role.name")
    List<Map<String, Object>> getMemberPerformanceStats(
            @Param("users") Collection<User> users,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
