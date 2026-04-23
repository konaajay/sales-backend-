package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.AttendancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, Long> {
    Optional<AttendancePolicy> findByOfficeId(Long officeId);
    boolean existsByOfficeId(Long officeId);
    void deleteByOfficeId(Long officeId);
}
