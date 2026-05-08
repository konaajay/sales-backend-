package com.lms.www.leadmanagement.entity;

public enum RoleType {
    ADMIN,
    MANAGER,
    TEAM_LEADER,
    ASSOCIATE;

    public static RoleType fromString(String role) {
        try {
            return RoleType.valueOf(role.toUpperCase());
        } catch (Exception e) {
            return ASSOCIATE;
        }
    }
}
