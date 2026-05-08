package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendanceShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceShiftRepository extends JpaRepository<AttendanceShift, Long> {
    
    Optional<AttendanceShift> findByName(String name);
    
    java.util.List<AttendanceShift> findByOfficeId(Long officeId);
}
