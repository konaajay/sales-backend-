package com.lms.www.leadmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.lms.www.leadmanagement.entity.Certificate;
import com.lms.www.leadmanagement.entity.CertificateStatus;

import java.util.List;

import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByStatus(CertificateStatus status);
    
    Optional<Certificate> findByCertificateId(String certificateId);
    
    @Query("SELECT COUNT(c) FROM Certificate c WHERE c.status = :status")
    Long countByStatus(CertificateStatus status);
    
    List<Certificate> findByStatusAndRetryCountLessThan(CertificateStatus status, Integer maxRetries);
    List<Certificate> findByStatusAndCreatedAtBefore(CertificateStatus status, java.time.LocalDateTime time);
}
