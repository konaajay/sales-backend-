package com.lms.www.leadmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.lms.www.leadmanagement.entity.Certificate;
import com.lms.www.leadmanagement.entity.CertificateStatus;
import com.lms.www.leadmanagement.repository.CertificateRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateScheduler {

    private final CertificateRepository certificateRepository;
    private final CertificateService certificateService;

    /**
     * Scheduled job to re-process certificates in RETRY status.
     * Runs every 5 minutes (300,000 milliseconds).
     */
    @Scheduled(fixedRate = 300000)
    public void reprocessRetryCertificates() {
        List<Certificate> retryRecords = certificateRepository.findByStatus(CertificateStatus.RETRY);
        
        if (retryRecords.isEmpty()) {
            return;
        }

        log.info("Scheduler picked up {} records in RETRY status for re-processing", retryRecords.size());

        for (Certificate certificate : retryRecords) {
            try {
                log.info("Re-processing certificate ID: {} (Retry count: {})", 
                        certificate.getCertificateId(), certificate.getRetryCount());
                
                // Move back to processing flow
                certificateService.processCertificate(certificate);
                
            } catch (Exception e) {
                log.error("Critical error during scheduled re-processing of certificate {}: {}", 
                        certificate.getCertificateId(), e.getMessage());
            }
        }
    }

    /**
     * Scheduled job to rescue certificates that are stuck in PROCESSING state.
     * This happens if the server crashes or an async thread dies unexpectedly.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    public void sweepStuckCertificates() {
        java.time.LocalDateTime tenMinsAgo = java.time.LocalDateTime.now().minusMinutes(10);
        List<Certificate> stuckRecords = certificateRepository.findByStatusAndCreatedAtBefore(CertificateStatus.PROCESSING, tenMinsAgo);

        if (stuckRecords.isEmpty()) {
            return;
        }

        log.warn("Found {} certificates stuck in PROCESSING status for >10 mins. Moving to RETRY.", stuckRecords.size());

        for (Certificate cert : stuckRecords) {
            cert.setStatus(CertificateStatus.RETRY);
            cert.setErrorMessage("Stuck in PROCESSING state. Likely due to network timeout or unexpected server restart.");
            certificateRepository.save(cert);
        }
    }
}
