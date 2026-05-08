package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.StudentFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StudentFeeRepository extends JpaRepository<StudentFee, Long> {
    Optional<StudentFee> findByLeadId(Long leadId);
    boolean existsByLeadId(Long leadId);

    @org.springframework.data.jpa.repository.Query("SELECT sf FROM StudentFee sf JOIN Lead l ON sf.leadId = l.id LEFT JOIN l.assignedTo a " +
            "WHERE (a.id IN :userIds OR (l.assignedTo IS NULL AND l.createdBy.id IN :userIds))")
    java.util.List<StudentFee> findFilteredByUserHierarchy(@org.springframework.data.repository.query.Param("userIds") java.util.Collection<Long> userIds);
}
