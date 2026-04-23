package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendanceSession;
import com.lms.www.leadmanagement.entity.AttendanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT s FROM AttendanceSession s WHERE s.user.id = :userId AND s.status IN :statuses")
    Optional<AttendanceSession> findActiveForUpdate(@Param("userId") Long userId, @Param("statuses") List<AttendanceStatus> statuses);

    @Query("SELECT s FROM AttendanceSession s WHERE s.status IN :statuses AND s.lastLocationTime < :cutoffTime")
    List<AttendanceSession> findInactiveSessions(@Param("statuses") List<AttendanceStatus> statuses, @Param("cutoffTime") LocalDateTime cutoffTime);

    List<AttendanceSession> findByUserIdOrderByCheckInTimeDesc(Long userId);

    Optional<AttendanceSession> findByUserIdAndStatusIn(Long userId, List<AttendanceStatus> statuses);

    List<AttendanceSession> findAllByStatusIn(List<AttendanceStatus> statuses);
    
    @Query("SELECT s FROM AttendanceSession s WHERE s.user.id = :userId AND s.checkInTime >= :startOfDay AND s.checkInTime <= :endOfDay")
    List<AttendanceSession> findSessionsForDate(@Param("userId") Long userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT s FROM AttendanceSession s WHERE s.user.id IN :userIds AND s.checkInTime >= :start AND s.checkInTime <= :end")
    List<AttendanceSession> findFilteredByUserIds(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    boolean existsByOfficeId(Long officeId);
    
    @Query("SELECT COUNT(DISTINCT s.user.id) FROM AttendanceSession s WHERE s.checkInTime >= :start AND s.checkInTime <= :end")
    long countPresentUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM AttendanceSession s WHERE s.isLate = true AND s.checkInTime >= :start AND s.checkInTime <= :end")
    long countLateUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM AttendanceSession s WHERE s.user.id IN :userIds AND s.checkInTime >= :start AND s.checkInTime <= :end")
    long countPresentUsersIn(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM AttendanceSession s WHERE s.user.id IN :userIds AND s.isLate = true AND s.checkInTime >= :start AND s.checkInTime <= :end")
    long countLateUsersIn(@Param("userIds") java.util.Collection<Long> userIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
