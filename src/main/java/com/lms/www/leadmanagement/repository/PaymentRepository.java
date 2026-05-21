package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentGatewayId = :paymentGatewayId")
    Optional<Payment> findByPaymentGatewayIdWithLock(@Param("paymentGatewayId") String paymentGatewayId);

    Optional<Payment> findTopByPaymentGatewayIdOrderByCreatedAtDesc(String paymentGatewayId);
    boolean existsByPaymentGatewayId(String paymentGatewayId);
    boolean existsByPaymentGatewayIdAndStatusNot(String paymentGatewayId, Payment.Status status);

    List<Payment> findByLeadIdIn(List<Long> leadIds);
    List<Payment> findByLeadId(Long leadId);

    List<Payment> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<Payment> findAllByStatus(Payment.Status status);
    
    List<Payment> findByLeadIdAndStatus(Long leadId, Payment.Status status);
    boolean existsByLeadIdAndStatus(Long leadId, Payment.Status status);
    
    void deleteByLeadIdAndStatusAndPaymentType(Long leadId, Payment.Status status, String paymentType);
    void deleteByLeadIdAndStatusAndPaymentTypeIn(Long leadId, Payment.Status status, List<String> paymentTypes);

    @Query("SELECT p FROM Payment p WHERE (:status IS NULL OR p.status = :status) " +
            "AND (:leadIds IS NULL OR p.leadId IN :leadIds) " +
            "AND ((:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND ((:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end) OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE)")
    List<Payment> findFiltered(
            @Param("leadIds") java.util.List<Long> leadIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end,
            @Param("status") Payment.Status status);

    @Query("SELECT p FROM Payment p JOIN Lead l ON p.leadId = l.id LEFT JOIN l.assignedTo a " +
            "WHERE (:status IS NULL OR p.status = :status) " +
            "AND (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND ((:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND ((:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end) OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "ORDER BY p.createdAt DESC")
    List<Payment> findFilteredByUserHierarchy(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end,
            @Param("status") Payment.Status status);


    @Query("SELECT p FROM Payment p WHERE (p.status = 'PAID' OR p.status = 'APPROVED' OR p.status = 'PENDING' OR p.status = 'COMPLETED' OR p.status = 'SUCCESS') " +
            "AND p.leadId IN (SELECT l.id FROM Lead l WHERE l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    List<Payment> findFilteredByUserIds(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(COALESCE(l.assignedTo.id, l.createdBy.id) as userId, sum(p.amount) as amount, count(distinct l.id) as successCount) " +
            "FROM Payment p JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.SUCCESS " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.COMPLETED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PARTIAL) " +
            "AND (:userIds IS NULL OR l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end) " +
            "GROUP BY COALESCE(l.assignedTo.id, l.createdBy.id)")
    List<java.util.Map<String, Object>> getRevenuePerUser(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(COALESCE(l.assignedTo.id, l.createdBy.id) as userId, sum(p.amount) as amount) " +
            "FROM Payment p JOIN Lead l ON p.leadId = l.id " +
            "WHERE p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "AND (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end) " +
            "GROUP BY COALESCE(l.assignedTo.id, l.createdBy.id)")
    List<java.util.Map<String, Object>> getPendingRevenuePerUser(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.SUCCESS " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PARTIAL) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    java.math.BigDecimal getGlobalTotalRevenue(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND p.dueDate < :now")
    java.math.BigDecimal getPendingRevenueAmount(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("now") java.time.LocalDateTime now);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND p.dueDate >= :now AND p.dueDate <= :future")
    java.math.BigDecimal getForecastRevenue(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("now") java.time.LocalDateTime now,
            @Param("future") java.time.LocalDateTime future);

    @Query("SELECT COUNT(p) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND p.dueDate < :now")
    long countPendingPayments(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("now") java.time.LocalDateTime now);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.SUCCESS " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.PARTIAL) " +
            "AND (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    java.math.BigDecimal getTotalRevenueIn(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND p.dueDate BETWEEN :start AND :end")
    long countPaymentsByDueDateBetween(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Payment p WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) AND p.dueDate < :now")
    long countGlobalPendingPayments(@Param("now") java.time.LocalDateTime now);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.dueDate BETWEEN :start AND :end")
    long countGlobalPaymentsByDueDateBetween(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) AND p.dueDate < :now")
    java.math.BigDecimal getGlobalPendingRevenueAmount(@Param("now") java.time.LocalDateTime now);

    @Query("SELECT COUNT(p) FROM Payment p WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE)")
    long countGlobalAllPending();

    @Query("SELECT COUNT(p) FROM Payment p JOIN Lead l ON p.leadId = l.id LEFT JOIN l.assignedTo a WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) AND (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds))")
    long countAllPendingByUserIds(@Param("userIds") java.util.Collection<Long> userIds);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) AND (:start IS NULL OR p.dueDate >= :start)")
    java.math.BigDecimal getGlobalTotalPendingRevenueIn(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p JOIN Lead l ON p.leadId = l.id LEFT JOIN l.assignedTo a WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) AND (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) AND (:start IS NULL OR p.dueDate >= :start)")
    java.math.BigDecimal getTotalPendingRevenueIn(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);
    @Query("SELECT new map(FUNCTION('DATE', p.createdAt) as date, sum(p.amount) as amount) " +
            "FROM Payment p WHERE UPPER(p.status) IN ('PAID', 'APPROVED', 'SUCCESS', 'PARTIAL', 'COMPLETED') " +
            "AND p.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', p.createdAt) ORDER BY FUNCTION('DATE', p.createdAt)")
    List<java.util.Map<String, Object>> getGlobalDailyRevenueTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', p.createdAt) as date, sum(p.amount) as amount) " +
            "FROM Payment p JOIN Lead l ON p.leadId = l.id LEFT JOIN l.assignedTo a " +
            "WHERE UPPER(p.status) IN ('PAID', 'APPROVED', 'SUCCESS', 'PARTIAL', 'COMPLETED') " +
            "AND (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND p.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', p.createdAt) ORDER BY FUNCTION('DATE', p.createdAt)")
    List<java.util.Map<String, Object>> getDailyRevenueTrendByIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', p.createdAt) as date, count(distinct p.leadId) as count) " +
            "FROM Payment p WHERE UPPER(p.status) IN ('PAID', 'APPROVED', 'SUCCESS', 'COMPLETED') " +
            "AND p.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', p.createdAt) ORDER BY FUNCTION('DATE', p.createdAt)")
    List<java.util.Map<String, Object>> getGlobalDailyConvertedTrend(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(FUNCTION('DATE', p.createdAt) as date, count(distinct p.leadId) as count) " +
            "FROM Payment p JOIN Lead l ON p.leadId = l.id " +
            "WHERE UPPER(p.status) IN ('PAID', 'APPROVED', 'SUCCESS', 'COMPLETED') " +
            "AND (l.assignedTo.id IN :userIds OR (l.assignedTo.id IS NULL AND l.createdBy.id IN :userIds)) " +
            "AND p.createdAt BETWEEN :start AND :end " +
            "GROUP BY FUNCTION('DATE', p.createdAt) ORDER BY FUNCTION('DATE', p.createdAt)")
    List<java.util.Map<String, Object>> getDailyConvertedTrendByIds(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    List<Payment> findByStatusInAndDueDateBetweenAndReminderSentFalse(
            java.util.Collection<Payment.Status> statuses,
            java.time.LocalDateTime start,
            java.time.LocalDateTime end);

    List<Payment> findByStatusInAndDueDateBetweenAndDueDateReminderSentFalse(
            java.util.Collection<Payment.Status> statuses,
            java.time.LocalDateTime start,
            java.time.LocalDateTime end);

    @Query("SELECT p FROM Payment p JOIN Lead l ON p.leadId = l.id LEFT JOIN l.assignedTo a " +
            "WHERE p.status = :status " +
            "AND (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds))")
    List<Payment> findPendingApprovalsByUserIds(@Param("userIds") java.util.Collection<Long> userIds, @Param("status") Payment.Status status);

    /**
     * Finds payments for leads assigned to the given user (as assignedTo or createdBy)
     * that were recently resolved (PAID or REJECTED) after a cutoff time.
     * Used to notify associates/team leads when their submitted manual payment is approved or rejected.
     */
    @Query("SELECT p FROM Payment p JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "   OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "   OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.REJECTED) " +
            "AND (l.assignedTo.id = :userId OR (l.assignedTo IS NULL AND l.createdBy.id = :userId)) " +
            "AND p.updatedAt >= :since")
    List<Payment> findRecentlyResolvedByUpdater(@Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);
}
