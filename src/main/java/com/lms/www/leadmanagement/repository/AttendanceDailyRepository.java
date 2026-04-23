package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendanceDaily;
import com.lms.www.leadmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceDailyRepository extends JpaRepository<AttendanceDaily, Long> {

    Optional<AttendanceDaily> findByUserIdAndDate(Long userId, LocalDate date);
    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    List<AttendanceDaily> findByDate(LocalDate date);
    List<AttendanceDaily> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<AttendanceDaily> findByUserIdOrderByDateDesc(Long userId);
    
    List<AttendanceDaily> findAllByUserInAndDateBetween(List<User> users, LocalDate startDate, LocalDate endDate);
    List<AttendanceDaily> findAllByUserInAndDate(List<User> users, LocalDate date);
    List<AttendanceDaily> findAllByUserInOrderByDateDesc(List<User> users);
}
