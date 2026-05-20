package com.lms.www.leadmanagement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.lms.www.leadmanagement.dto.StatsResponse;
import com.lms.www.leadmanagement.dto.UploadResponse;
import com.lms.www.leadmanagement.entity.Certificate;
import com.lms.www.leadmanagement.exception.ResourceNotFoundException;
import com.lms.www.leadmanagement.repository.CertificateRepository;
import com.lms.www.leadmanagement.service.CertificateService;
import com.lms.www.leadmanagement.service.FileStorageService;

import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/certificates")
@Tag(name = "Certificate Management", description = "Bulk Certificate Generation APIs")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final FileStorageService fileStorageService;
    private final CertificateRepository certificateRepository;

    @PostMapping("/upload-csv")
    @Operation(summary = "Upload CSV for bulk certificate processing")
    public ResponseEntity<UploadResponse> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("webinarName") String webinarName) {
        UploadResponse response = certificateService.processCsvUpload(file, webinarName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/single")
    @Operation(summary = "Generate a single certificate")
    public ResponseEntity<UploadResponse> generateSingleCertificate(@RequestBody com.lms.www.leadmanagement.dto.CertificateRequest request) {
        UploadResponse response = certificateService.processSingleRequest(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all certificates")
    public ResponseEntity<List<Certificate>> getAllCertificates() {
        return ResponseEntity.ok(certificateService.getAllCertificates());
    }

    @GetMapping("/sent")
    @Operation(summary = "Get successfully sent certificates")
    public ResponseEntity<List<Certificate>> getSentCertificates() {
        return ResponseEntity.ok(certificateService.getSentCertificates());
    }

    @GetMapping("/failed")
    @Operation(summary = "Get failed certificates")
    public ResponseEntity<List<Certificate>> getFailedCertificates() {
        return ResponseEntity.ok(certificateService.getFailedCertificates());
    }

    @GetMapping("/stats")
    @Operation(summary = "Get processing statistics")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(certificateService.getCertificateStats());
    }

    @PostMapping("/retry-failed")
    @Operation(summary = "Retry all failed certificates")
    public ResponseEntity<String> retryFailed() {
        certificateService.retryFailedCertificates();
        return ResponseEntity.ok("Retry process started for failed certificates");
    }

    @GetMapping("/download/{certificateId}")
    @Operation(summary = "Download a certificate PDF")
    public ResponseEntity<Resource> downloadCertificate(@PathVariable String certificateId) {
        Certificate certificate = certificateRepository.findByCertificateId(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found: " + certificateId));

        if (certificate.getStoragePath() == null) {
            throw new RuntimeException("No file stored for certificate: " + certificateId);
        }

        byte[] data = fileStorageService.getFile(certificate.getStoragePath());
        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate_" + certificateId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(data.length)
                .body(resource);
    }

    @GetMapping("/preview/{certificateId}")
    @Operation(summary = "Preview a certificate PDF in browser")
    public ResponseEntity<Resource> previewCertificate(@PathVariable String certificateId) {
        Certificate certificate = certificateRepository.findByCertificateId(certificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found: " + certificateId));

        if (certificate.getStoragePath() == null) {
            throw new RuntimeException("No file stored for certificate: " + certificateId);
        }

        byte[] data = fileStorageService.getFile(certificate.getStoragePath());
        ByteArrayResource resource = new ByteArrayResource(data);

        // Uses "inline" instead of "attachment" so browsers will display the PDF rather than downloading it immediately
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate_" + certificateId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(data.length)
                .body(resource);
    }
}
