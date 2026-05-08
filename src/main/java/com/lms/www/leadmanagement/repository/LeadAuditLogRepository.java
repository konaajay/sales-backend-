package com.lms.www.leadmanagement.repository;

import com.lms.www.leadmanagement.entity.LeadAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LeadAuditLogRepository extends JpaRepository<LeadAuditLog, Long> {
    List<LeadAuditLog> findByLeadIdOrderByTimestampDesc(Long leadId);
}
