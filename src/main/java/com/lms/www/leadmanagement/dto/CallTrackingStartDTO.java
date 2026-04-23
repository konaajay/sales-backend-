package com.lms.www.leadmanagement.dto;

import lombok.Data;

@Data
public class CallTrackingStartDTO {
    private Long leadId;
    private String phoneNumber;
}
