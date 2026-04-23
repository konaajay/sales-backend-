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
    private Long officeId;
    private String officeName;
    private java.math.BigDecimal monthlyTarget;
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

        UserDTO dto = UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .managerId(user.getManager() != null ? user.getManager().getId() : null)
                .managerName(user.getManager() != null ? user.getManager().getName() : null)
                .supervisorId(user.getSupervisor() != null ? user.getSupervisor().getId() : null)
                .supervisorName(user.getSupervisor() != null ? user.getSupervisor().getName() : null)
                .reportScope(user.getReportScope())
                .active(user.isActive())
                .shiftId(user.getShift() != null ? user.getShift().getId() : null)
                .shiftName(user.getShift() != null ? user.getShift().getName() : null)
                .officeId(user.getAssignedOffice() != null ? user.getAssignedOffice().getId() : null)
                .officeName(user.getAssignedOffice() != null ? user.getAssignedOffice().getName() : null)
                .monthlyTarget(user.getMonthlyTarget())
                .joiningDate(user.getJoiningDate())
                .build();

        if (user.getDirectPermissions() != null && !user.getDirectPermissions().isEmpty()) {
            dto.setPermissions(user.getDirectPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toList()));
        } else if (user.getRole() != null && user.getRole().getPermissions() != null) {
            dto.setPermissions(user.getRole().getPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toList()));
        } else {
            dto.setPermissions(new java.util.ArrayList<>());
        }

        java.util.List<User> allSubs = new java.util.ArrayList<>();
        if (includeSubordinates) {
            if (user.getSubordinates() != null)
                allSubs.addAll(user.getSubordinates());
            if (user.getManagedAssociates() != null) {
                for (User assoc : user.getManagedAssociates()) {
                    if (!allSubs.contains(assoc))
                        allSubs.add(assoc);
                }
            }
        }

        if (includeSubordinates && !allSubs.isEmpty()) {
            dto.setSubordinates(allSubs.stream()
                    .map(u -> fromEntity(u, true))
                    .collect(java.util.stream.Collectors.toList()));
        } else {
            dto.setSubordinates(new java.util.ArrayList<>());
        }
        return dto;
    }
}
