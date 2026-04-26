package com.lms.www.leadmanagement.entity;

public enum LeadStatus {
    NEW,
    WORKING,
    CONTACTED,
    INTERESTED,
    UNDER_REVIEW,
    FOLLOW_UP,
    CONVERTED,
    PAID,
    EMI,
    SUCCESS,
    REJECTED,
    REFUND,
    LOST,
    NOT_INTERESTED,
    CLOSED,
    COMPLETED;

    public static LeadStatus fromString(String status) {
        if (status == null) return NEW;
        try {
            String sanitized = status.toUpperCase().replace(" ", "_");
            if ("FOLLOWUP".equals(sanitized)) return FOLLOW_UP;
            return LeadStatus.valueOf(sanitized);
        } catch (Exception e) {
            return NEW;
        }
    }
}
