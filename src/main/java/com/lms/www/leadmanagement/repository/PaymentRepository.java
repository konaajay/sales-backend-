package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentGatewayId = :paymentGatewayId")
    Optional<Payment> findByPaymentGatewayIdWithLock(@Param("paymentGatewayId") String paymentGatewayId);

    Optional<Payment> findByPaymentGatewayId(String paymentGatewayId);

    List<Payment> findByLeadIdIn(List<Long> leadIds);

    List<Payment> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<Payment> findAllByStatus(Payment.Status status);
    
    List<Payment> findByLeadIdAndStatus(Long leadId, Payment.Status status);

    @Query("SELECT p FROM Payment p WHERE (:status IS NULL OR p.status = :status) " +
            "AND (:leadIds IS NULL OR p.leadId IN :leadIds) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    List<Payment> findFiltered(
            @Param("leadIds") java.util.List<Long> leadIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end,
            @Param("status") Payment.Status status);


    @Query("SELECT p FROM Payment p WHERE (p.status = 'PAID' OR p.status = 'APPROVED' OR p.status = 'PENDING') " +
            "AND p.leadId IN (SELECT l.id FROM Lead l WHERE l.assignedTo.id IN :userIds) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    List<Payment> findFilteredByUserIds(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(l.assignedTo.id as userId, sum(p.amount) as amount) " +
            "FROM Payment p JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.SUCCESS) " +
            "AND l.assignedTo.id IN :userIds " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end) " +
            "GROUP BY l.assignedTo.id")
    List<java.util.Map<String, Object>> getRevenuePerUser(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT new map(l.assignedTo.id as userId, sum(p.amount) as amount) " +
            "FROM Payment p JOIN Lead l ON p.leadId = l.id " +
            "WHERE p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "AND l.assignedTo.id IN :userIds " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end) " +
            "GROUP BY l.assignedTo.id")
    List<java.util.Map<String, Object>> getPendingRevenuePerUser(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.SUCCESS) " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    java.math.BigDecimal getGlobalTotalRevenue(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND l.assignedTo.id IN :userIds " +
            "AND p.dueDate < :now")
    java.math.BigDecimal getPendingRevenueAmount(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("now") java.time.LocalDateTime now);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND l.assignedTo.id IN :userIds " +
            "AND p.dueDate >= :now AND p.dueDate <= :future")
    java.math.BigDecimal getForecastRevenue(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("now") java.time.LocalDateTime now,
            @Param("future") java.time.LocalDateTime future);

    @Query("SELECT COUNT(p) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PENDING " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.OVERDUE) " +
            "AND l.assignedTo.id IN :userIds " +
            "AND p.dueDate < :now")
    long countPendingPayments(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("now") java.time.LocalDateTime now);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "JOIN Lead l ON p.leadId = l.id " +
            "WHERE (p.status = com.lms.www.leadmanagement.entity.Payment$Status.PAID " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.APPROVED " +
            "OR p.status = com.lms.www.leadmanagement.entity.Payment$Status.SUCCESS) " +
            "AND l.assignedTo.id IN :userIds " +
            "AND (:start IS NULL OR COALESCE(p.dueDate, p.createdAt) >= :start) " +
            "AND (:end IS NULL OR COALESCE(p.dueDate, p.createdAt) <= :end)")
    java.math.BigDecimal getTotalRevenueIn(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);
}
