package com.lms.www.leadmanagement.dto;

import lombok.Data;

@Data
public class RegistrationRequest {
    private Long webinarId;
    private String fullName;
    private String email;
    private String phone;
    private String collegeName;
    private String department;
    private String yearOfStudy;
    private String referralSource;
    private boolean confirmation;
    private String crName;
    private String crPhone;
    private String friendName;
    private String friendPhone;
    private String friendCollege;
}
