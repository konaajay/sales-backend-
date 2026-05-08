package com.lms.www.leadmanagement.dto;

import com.lms.www.leadmanagement.entity.User;
import com.lms.www.leadmanagement.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String mobile;
    private String role;
    private String password;
    private Long managerId;
    private String managerName;
    private Long supervisorId;
    private String supervisorName;
    private java.util.List<String> permissions;
    private com.lms.www.leadmanagement.entity.ReportScope reportScope;
    private boolean active;
    private Long shiftId;
    private String shiftName;
    private String shiftTime;

    private Long officeId;
    private String officeName;
    private Double latitude;
    private Double longitude;


    private java.util.List<UserDTO> subordinates;
    private java.time.LocalDate joiningDate;

    private static final ThreadLocal<java.util.Set<Long>> visitedIds = ThreadLocal.withInitial(java.util.HashSet::new);

    public static UserDTO fromEntity(User user) {
        try {
            return fromEntity(user, false);
        } finally {
            visitedIds.get().clear();
        }
    }

    public static UserDTO fromEntityWithTree(User user) {
        try {
            return fromEntity(user, true);
        } finally {
            visitedIds.get().clear();
        }
    }

    private static UserDTO fromEntity(User user, boolean includeSubordinates) {
        if (user == null)
            return null;

        // Circular reference check
        if (includeSubordinates) {
            if (visitedIds.get().contains(user.getId())) {
                return UserDTO.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole() != null ? user.getRole().getName() : null)
                        .build(); // Return partial DTO to break cycle
            }
            visitedIds.get().add(user.getId());
        }

        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setMobile(user.getMobile());
        dto.setRole(user.getRole() != null ? user.getRole().getName() : null);
        
        try {
            if (user.getManager() != null) {
                dto.setManagerId(user.getManager().getId());
                // Avoid calling getName() on proxy if possible, or handle it
                try {
                    dto.setManagerName(user.getManager().getName());
                } catch (Exception e) {
                    dto.setManagerName("Assigned (ID: " + user.getManager().getId() + ")");
                }
            }
        } catch (Exception e) { 
            dto.setManagerName("Loading..."); 
        }

        try {
            if (user.getSupervisor() != null) {
                dto.setSupervisorId(user.getSupervisor().getId());
                try {
                    dto.setSupervisorName(user.getSupervisor().getName());
                } catch (Exception e) {
                    dto.setSupervisorName("Assigned (ID: " + user.getSupervisor().getId() + ")");
                }
            }
        } catch (Exception e) {
            dto.setSupervisorName("Loading...");
        }

        dto.setReportScope(user.getReportScope());
        dto.setActive(user.isActive());

        dto.setJoiningDate(user.getJoiningDate());

        try {
            if (user.getShift() != null) {
                dto.setShiftId(user.getShift().getId());
                dto.setShiftName(user.getShift().getName());
                dto.setShiftTime(user.getShift().getName() + " (" + user.getShift().getStartTime() + " - " + user.getShift().getEndTime() + ")");
            } else {
                dto.setShiftTime("Not Assigned");
            }
        } catch (Exception e) {
            dto.setShiftTime("Loading...");
        }

        try {
            if (user.getAssignedOffice() != null) {
                dto.setOfficeId(user.getAssignedOffice().getId());
                dto.setOfficeName(user.getAssignedOffice().getName());
                dto.setLatitude(user.getAssignedOffice().getLatitude());
                dto.setLongitude(user.getAssignedOffice().getLongitude());
            }
        } catch (Exception e) {
            dto.setOfficeName("Loading...");
        }

        try {
            if (user.getDirectPermissions() != null && !user.getDirectPermissions().isEmpty()) {
                dto.setPermissions(user.getDirectPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toList()));
            } else if (user.getRole() != null) {
                // Safely handle role permissions
                java.util.Set<Permission> rolePerms = null;
                try {
                    rolePerms = user.getRole().getPermissions();
                } catch (Exception e) {}
                
                if (rolePerms != null) {
                    dto.setPermissions(rolePerms.stream()
                        .map(Permission::getName)
                        .collect(Collectors.toList()));
                } else {
                    dto.setPermissions(new java.util.ArrayList<>());
                }
            } else {
                dto.setPermissions(new java.util.ArrayList<>());
            }
        } catch (Exception e) {
            dto.setPermissions(new java.util.ArrayList<>());
        }

        if (includeSubordinates) {
            java.util.List<User> allReports = new java.util.ArrayList<>();
            try {
                if (user.getDirectReports() != null) allReports.addAll(user.getDirectReports());
            } catch (Exception e) { /* ignore lazy load issues */ }
            
            try {
                if (user.getManagedSubordinates() != null) {
                    for (User u : user.getManagedSubordinates()) {
                        if (!allReports.contains(u)) allReports.add(u);
                    }
                }
            } catch (Exception e) { /* ignore lazy load issues */ }
            
            if (!allReports.isEmpty()) {
                dto.setSubordinates(allReports.stream()
                        .filter(u -> u != null && !u.getId().equals(user.getId())) // Extra safety
                        .map(u -> fromEntity(u, true))
                        .collect(java.util.stream.Collectors.toList()));
            } else {
                dto.setSubordinates(new java.util.ArrayList<>());
            }
        } else {
            dto.setSubordinates(new java.util.ArrayList<>());
        }
        return dto;
    }
}
