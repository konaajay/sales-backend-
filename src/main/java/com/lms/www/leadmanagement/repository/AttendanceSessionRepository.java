package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendanceSession;
import com.lms.www.leadmanagement.entity.AttendanceStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
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

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
            """)
    List<AttendanceSession> findAllByUserIdAndStatusIn(
            @org.springframework.data.repository.query.Param("userId") Long userId, 
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
                ORDER BY s.checkInTime DESC
            """)
    List<AttendanceSession> _findActiveSession(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            Pageable pageable);

    default Optional<AttendanceSession> findActiveSession(Long userId, List<AttendanceStatus> statuses) {
        return _findActiveSession(userId, statuses, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.checkInTime < :endOfDay
                AND (s.checkOutTime IS NULL OR s.checkOutTime > :startOfDay)
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
            """)
    List<AttendanceSession> findSessionsForDate(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("startOfDay") LocalDateTime startOfDay,
            @org.springframework.data.repository.query.Param("endOfDay") LocalDateTime endOfDay);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id IN :userIds
                AND (s.user.joiningDate IS NULL OR s.user.joiningDate <= :end)
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
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
            """)
    List<AttendanceSession> _findByUserIdAndStatusIn(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            Pageable pageable);

    default Optional<AttendanceSession> findByUserIdAndStatusIn(Long userId, List<AttendanceStatus> statuses) {
        return _findByUserIdAndStatusIn(userId, statuses, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
                ORDER BY s.checkInTime DESC
            """)
    List<AttendanceSession> _findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            Pageable pageable);

    default Optional<AttendanceSession> findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(Long userId,
            List<AttendanceStatus> statuses) {
        return _findFirstByUserIdAndStatusInOrderByCheckInTimeDesc(userId, statuses, PageRequest.of(0, 1)).stream()
                .findFirst();
    }

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.status IN :statuses
                AND (s.lastSeenTime IS NULL OR s.lastSeenTime < :cutoffTime)
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
            """)
    List<AttendanceSession> findInactiveSessions(
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            @org.springframework.data.repository.query.Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.user.id = :userId
                AND s.status IN :statuses
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
                ORDER BY s.checkInTime DESC
            """)
    List<AttendanceSession> findLatestStatusNoLock(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses,
            Pageable pageable);

    @Query("""
                SELECT COUNT(DISTINCT s.user.id)
                FROM AttendanceSession s
                WHERE (s.user.joiningDate IS NULL OR s.user.joiningDate <= :end)
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    long countPresentUsers(
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);

    @Query("""
                SELECT COUNT(DISTINCT s.user.id)
                FROM AttendanceSession s
                WHERE (s.user.joiningDate IS NULL OR s.user.joiningDate <= :end)
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
                AND (s.user.joiningDate IS NULL OR s.user.joiningDate <= :end)
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
                AND (s.user.joiningDate IS NULL OR s.user.joiningDate <= :end)
                AND s.isLate = true
                AND (s.checkInTime <= :end
                AND (s.checkOutTime IS NULL OR s.checkOutTime >= :start))
            """)
    long countLateUsersIn(
            @org.springframework.data.repository.query.Param("userIds") Collection<Long> userIds,
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end);

    boolean existsByOfficeId(Long officeId);
    
    @Query("SELECT s FROM AttendanceSession s WHERE s.user.id = :userId ORDER BY s.checkInTime DESC")
    List<AttendanceSession> _findFirstByUserIdOrderByCheckInTimeDesc(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);

    default Optional<AttendanceSession> findFirstByUserIdOrderByCheckInTimeDesc(Long userId) {
        return _findFirstByUserIdOrderByCheckInTimeDesc(userId, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("""
                SELECT s FROM AttendanceSession s
                WHERE s.status IN :statuses
                AND (s.user.joiningDate IS NULL OR s.checkInTime >= s.user.joiningDate)
            """)
    List<AttendanceSession> findAllByStatusIn(@org.springframework.data.repository.query.Param("statuses") List<AttendanceStatus> statuses);
}