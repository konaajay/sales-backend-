package com.lms.www.leadmanagement.entity;

public enum AttendanceStatus {
    WORKING,
    ON_SHORT_BREAK, // Manual short break
    ON_LONG_BREAK,  // Manual long break
    ON_BREAK,       // Legacy manual break (kept for historical database rows)
    AUTO_BREAK,    // System auto break
    OUTSIDE,       // Outside office radius
    PUNCHED_OUT,   // Logged out
    ABSENT,        // Not logged in for the day
    NOT_STARTED    // Future date
}
