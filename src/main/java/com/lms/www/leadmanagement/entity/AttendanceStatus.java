package com.lms.www.leadmanagement.entity;

public enum AttendanceStatus {
    WORKING,
    ON_BREAK,      // Manual break
    AUTO_BREAK,    // System auto break
    OUTSIDE,       // Outside office radius
    PUNCHED_OUT,   // Logged out
    ABSENT,        // Not logged in for the day
    NOT_STARTED    // Future date
}
