package com.lms.www.leadmanagement.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CertificateRequest {
    private String studentName;
    private String webinarName;
    private String email;
    private String issueDate;
}
