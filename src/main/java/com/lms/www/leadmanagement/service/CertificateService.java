package com.lms.www.leadmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lms.www.leadmanagement.dto.CertificateRequest;
import com.lms.www.leadmanagement.dto.StatsResponse;
import com.lms.www.leadmanagement.dto.UploadResponse;
import com.lms.www.leadmanagement.entity.Certificate;
import com.lms.www.leadmanagement.entity.CertificateStatus;
import com.lms.www.leadmanagement.entity.Registration;
import com.lms.www.leadmanagement.repository.CertificateRepository;
import com.lms.www.leadmanagement.util.CertificateIdGenerator;
import com.lms.www.leadmanagement.util.CsvParser;
import com.lms.www.leadmanagement.util.DateFormatterUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificateIdGenerator idGenerator;
    private final EmailService emailService;
    private final CsvParser csvParser;
    private static final int MAX_RETRIES = 3;

    public UploadResponse processCsvUpload(MultipartFile file, String webinarName) {
        List<CertificateRequest> requests = csvParser.parseCsv(file, webinarName);
        int validCount = 0;

        List<Certificate> certificatesToSave = new java.util.ArrayList<>();
        for (CertificateRequest request : requests) {
            if (isValidRequest(request)) {
                certificatesToSave.add(buildPendingCertificate(request, null));
                validCount++;
            }
        }

        if (!certificatesToSave.isEmpty()) {
            certificateRepository.saveAll(certificatesToSave);
        }

        processPendingCertificatesAsync();
        return new UploadResponse("Processing started for " + validCount + " valid records");
    }

    public UploadResponse processSingleRequest(CertificateRequest request, Registration registration) {
        if (request.getIssueDate() == null || request.getIssueDate().trim().isEmpty()) {
            request.setIssueDate(LocalDate.now().toString());
        }
        
        if (isValidRequest(request)) {
            createPendingCertificate(request, registration);
            processPendingCertificatesAsync();
            return new UploadResponse("Processing started for 1 record");
        }
        return new UploadResponse("Invalid request data: please check name, email, webinar name, and issue date");
    }

    public UploadResponse processSingleRequest(CertificateRequest request) {
        return processSingleRequest(request, null);
    }

    private boolean isValidRequest(CertificateRequest request) {
        return request.getStudentName() != null && !request.getStudentName().trim().isEmpty() &&
               request.getWebinarName() != null && !request.getWebinarName().trim().isEmpty() &&
               request.getIssueDate() != null && !request.getIssueDate().trim().isEmpty() &&
               request.getEmail() != null && request.getEmail().contains("@");
    }

    private Certificate buildPendingCertificate(CertificateRequest request, Registration registration) {
        Certificate certificate = new Certificate();
        certificate.setStudentName(request.getStudentName());
        certificate.setWebinarName(request.getWebinarName());
        certificate.setEmail(request.getEmail());
        certificate.setIssueDate(DateFormatterUtil.formatToStandard(request.getIssueDate()));
        certificate.setCertificateId(idGenerator.generateCertificateId());
        certificate.setStatus(CertificateStatus.PENDING);
        certificate.setRegistration(registration);
        return certificate;
    }

    @Transactional
    public Certificate createPendingCertificate(CertificateRequest request, Registration registration) {
        Certificate certificate = buildPendingCertificate(request, registration);
        return certificateRepository.save(certificate);
    }

    @Transactional
    public Certificate createPendingCertificate(CertificateRequest request) {
        return createPendingCertificate(request, null);
    }

    public void processPendingCertificatesAsync() {
        CompletableFuture.runAsync(() -> {
            List<Certificate> pendingCertificates = certificateRepository.findByStatus(CertificateStatus.PENDING);
            log.info("Processing {} pending certificates", pendingCertificates.size());

            pendingCertificates.forEach(this::processCertificateAsync);
        });
    }

    private CompletableFuture<Void> processCertificateAsync(Certificate certificate) {
        return CompletableFuture.runAsync(() -> {
            try {
                processCertificate(certificate);
            } catch (Exception e) {
                handleCertificateFailure(certificate, e.getMessage());
            }
        });
    }

    @Transactional
    public void processCertificate(Certificate certificate) {
        certificate.setStatus(CertificateStatus.PROCESSING);
        certificateRepository.save(certificate);

        try {
            emailService.sendCertificateEmail(certificate, certificate.getRetryCount());
            certificate.setStatus(CertificateStatus.SENT);
            certificate.setErrorMessage(null); // Clear previous errors on success
            
            // Update Registration status if linked
            if (certificate.getRegistration() != null) {
                Registration reg = certificate.getRegistration();
                reg.setCertificateSent(true);
                reg.setCertificateId(certificate.getCertificateId());
            }
        } catch (Exception e) {
            handleCertificateFailure(certificate, e.getMessage());
        } finally {
            certificateRepository.save(certificate);
        }
    }

    @Transactional
    public void handleCertificateFailure(Certificate certificate, String errorMessage) {
        // Truncate error message if it's extremely long
        if (errorMessage != null && errorMessage.length() > 5000) {
            errorMessage = errorMessage.substring(0, 4997) + "...";
        }
        certificate.setErrorMessage(errorMessage);

        boolean retryable = isRetryable(errorMessage);

        if (retryable) {
            certificate.setRetryCount(certificate.getRetryCount() + 1);
            if (certificate.getRetryCount() < MAX_RETRIES) {
                certificate.setStatus(CertificateStatus.RETRY);
                log.warn("Certificate {} moved to RETRY (attempt {}/{}): {}", 
                        certificate.getCertificateId(), certificate.getRetryCount(), MAX_RETRIES, errorMessage);
            } else {
                certificate.setStatus(CertificateStatus.FAILED);
                log.error("Certificate {} permanently FAILED after {} retries: {}", 
                         certificate.getCertificateId(), MAX_RETRIES, errorMessage);
            }
        } else {
            certificate.setStatus(CertificateStatus.FAILED);
            log.error("Certificate {} permanently FAILED (non-retryable): {}", 
                     certificate.getCertificateId(), errorMessage);
        }
    }

    private boolean isRetryable(String errorMessage) {
        if (errorMessage == null) return false;
        String lower = errorMessage.toLowerCase();
        
        // Non-retryable errors (STRICT)
        if (lower.contains("invalid email") || lower.contains("missing") || lower.contains("corrupt")) {
            return false;
        }

        // Retryable errors (STRICT)
        // SMTP failure, Network timeout, Temporary service issues
        return lower.contains("smtp") || lower.contains("timeout") || lower.contains("temporary") || 
               lower.contains("connection") || lower.contains("service issues");
    }

    public List<Certificate> getAllCertificates() {
        return certificateRepository.findAll();
    }

    public List<Certificate> getSentCertificates() {
        return certificateRepository.findByStatus(CertificateStatus.SENT);
    }

    public List<Certificate> getFailedCertificates() {
        return certificateRepository.findByStatus(CertificateStatus.FAILED);
    }

    @Transactional
    public void retryFailedCertificates() {
        List<Certificate> retryable = certificateRepository
                .findByStatusAndRetryCountLessThan(CertificateStatus.FAILED, MAX_RETRIES);
        log.info("Retrying {} failed certificates", retryable.size());
        retryable.forEach(cert -> {
            cert.setStatus(CertificateStatus.PENDING);
            cert.setRetryCount(0);
            certificateRepository.save(cert);
        });
        processPendingCertificatesAsync();
    }

    public StatsResponse getCertificateStats() {
        Map<String, Long> summary = Map.of(
            "TOTAL", certificateRepository.count(),
            "SENT", certificateRepository.countByStatus(CertificateStatus.SENT),
            "PENDING", certificateRepository.countByStatus(CertificateStatus.PENDING),
            "PROCESSING", certificateRepository.countByStatus(CertificateStatus.PROCESSING),
            "RETRY", certificateRepository.countByStatus(CertificateStatus.RETRY),
            "FAILED", certificateRepository.countByStatus(CertificateStatus.FAILED)
        );
        return new StatsResponse(summary, certificateRepository.findAll());
    }
}
