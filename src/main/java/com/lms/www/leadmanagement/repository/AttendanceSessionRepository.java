package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendanceSession;
import com.lms.www.leadmanagement.entity.AttendanceStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND s.checkInTime >= s.user.joiningDate
                ORDER BY s.checkInTime DESC
            """)
    Optional<AttendanceSession> findActiveSession(
            Long userId,
            List<AttendanceStatus> statuses);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.user.joiningDate <= :endOfDay
                AND (s.checkInTime <= :endOfDay
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :startOfDay))
            """)
    List<AttendanceSession> findSessionsForDate(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id IN :userIds
                AND s.user.joiningDate <= :end
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    Page<AttendanceSession> findFilteredByUserIds(
            Collection<Long> userIds,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND s.checkInTime >= s.user.joiningDate
            """)
    Optional<AttendanceSession> findByUserIdAndStatusIn(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND s.checkInTime >= s.user.joiningDate
                ORDER BY s.checkInTime DESC
            """)
    Optional<AttendanceSession> findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.status IN :statuses
                AND (s.lastLocationTime IS NULL OR s.lastLocationTime < :cutoffTime)
                AND s.checkInTime >= s.user.joiningDate
            """)
    List<AttendanceSession> findInactiveSessions(
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            @org.springframework.data.repository.query.Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND s.checkInTime >= s.user.joiningDate
                ORDER BY s.checkInTime DESC
            """)
    List<AttendanceSession> findLatestStatusNoLock(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            Pageable pageable);

    @Query("""
                SELECT COUNT(DISTINCT s.user.id)
                FROM AttendanceSession s
                WHERE s.user.joiningDate <= :end
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    long countPresentUsers(
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);

    @Query("""
                SELECT COUNT(DISTINCT s.user.id)
                FROM AttendanceSession s
                WHERE s.user.joiningDate <= :end
                AND s.isLate = true
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    long countLateUsers(
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);

    @Query("""
                SELECT COUNT(DISTINCT s.user.id)
                FROM AttendanceSession s
                WHERE s.user.id IN :userIds
                AND s.user.joiningDate <= :end
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    long countPresentUsersIn(
            @org.springframework.data.repository.query.Param("userIds") Collection<Long> userIds,
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);

    @Query("""
                SELECT COUNT(DISTINCT s.user.id)
                FROM AttendanceSession s
                WHERE s.user.id IN :userIds
                AND s.user.joiningDate <= :end
                AND s.isLate = true
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    long countLateUsersIn(
            @org.springframework.data.repository.query.Param("userIds") Collection<Long> userIds,
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);
}