package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendanceDaily;
import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceDailyRepository extends JpaRepository<AttendanceDaily, Long> {

    // ✅ Safe single record - REMOVED joiningDate check to prevent duplicates if joiningDate is null
    @Query("""
                SELECT a FROM AttendanceDaily a
                WHERE a.user.id = :userId
                AND a.date = :date
                AND (a.user.joiningDate IS NULL OR a.date >= a.user.joiningDate)
                ORDER BY a.createdAt DESC
            """)
    List<AttendanceDaily> findByUserIdAndDate(Long userId, LocalDate date);

    default Optional<AttendanceDaily> findSingleByUserIdAndDate(Long userId, LocalDate date) {
        return findByUserIdAndDate(userId, date).stream().findFirst();
    }

    // ✅ Safe existence check
    @Query("""
                SELECT COUNT(a) > 0 FROM AttendanceDaily a
                WHERE a.user.id = :userId
                AND a.date = :date
                AND (a.user.joiningDate IS NULL OR a.date >= a.user.joiningDate)
            """)
    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    // ✅ Safe user history
    @Query("""
                SELECT a FROM AttendanceDaily a
                WHERE a.user.id = :userId
                AND (a.user.joiningDate IS NULL OR a.date >= a.user.joiningDate)
                ORDER BY a.date DESC
            """)
    List<AttendanceDaily> findByUserIdOrderByDateDesc(Long userId);

    // ✅ Safe user range
    @Query("""
                SELECT a FROM AttendanceDaily a
                WHERE a.user.id = :userId
                AND a.date BETWEEN :start AND :end
                AND (a.user.joiningDate IS NULL OR a.date >= a.user.joiningDate)
                ORDER BY a.date DESC
            """)
    List<AttendanceDaily> findValidUserAttendanceBetween(
            Long userId,
            LocalDate start,
            LocalDate end);

    // ✅ Safe multi-user range (dashboard)
    @Query("""
                SELECT a FROM AttendanceDaily a
                WHERE a.user.id IN :userIds
                AND a.date BETWEEN :start AND :end
                AND (a.user.joiningDate IS NULL OR a.date >= a.user.joiningDate)
            """)
    List<AttendanceDaily> findAllByUserIdInAndDateBetween(
            @org.springframework.data.repository.query.Param("userIds") List<Long> userIds,
            @org.springframework.data.repository.query.Param("start") LocalDate start,
            @org.springframework.data.repository.query.Param("end") LocalDate end);


    // ✅ Safe single day (dashboard)
    @Query("""
                SELECT a FROM AttendanceDaily a
                WHERE a.user IN :users
                AND a.date = :date
                AND (a.user.joiningDate IS NULL OR a.date >= a.user.joiningDate)
            """)
    List<AttendanceDaily> findAllByUserInAndDate(
            List<User> users,
            LocalDate date);
}