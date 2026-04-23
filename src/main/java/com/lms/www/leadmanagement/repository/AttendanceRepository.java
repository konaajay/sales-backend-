package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.Attendance;
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
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT a FROM Attendance a WHERE a.user.id = :userId AND a.status IN :statuses")
    Optional<Attendance> findActiveForUpdate(@Param("userId") Long userId, @Param("statuses") List<AttendanceStatus> statuses);

    @Query("SELECT a FROM Attendance a WHERE a.status IN :statuses AND a.lastLocationTime < :cutoffTime")
    List<Attendance> findInactiveAttendances(@Param("statuses") List<AttendanceStatus> statuses, @Param("cutoffTime") LocalDateTime cutoffTime);

    List<Attendance> findByUserIdOrderByCheckInTimeDesc(Long userId);
    
    Optional<Attendance> findByUserIdAndStatusIn(Long userId, List<AttendanceStatus> statuses);

    List<Attendance> findAllByStatusIn(List<AttendanceStatus> statuses);
}
