package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.StudentFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StudentFeeRepository extends JpaRepository<StudentFee, Long> {
    Optional<StudentFee> findByLeadId(Long leadId);
    boolean existsByLeadId(Long leadId);
}
