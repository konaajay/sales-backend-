package com.lms.www.leadmanagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lms.www.leadmanagement.entity.CertificateStatus;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CertificateDto {
    private Long id;
    private String studentName;
    private String webinarName;
    private String email;
    private String certificateId;
    
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDateTime issueDate;
    private CertificateStatus status;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
}
