package com.lms.www.leadmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private Long id;
    private String name;
    private java.util.List<String> permissions;

    public static RoleDTO fromEntity(com.lms.www.leadmanagement.entity.Role role) {
        if (role == null) return null;
        return RoleDTO.builder()
                .id(role.getId())
                .name(role.getName())
                .permissions(role.getPermissions() != null 
                    ? role.getPermissions().stream().map(p -> p.getName()).collect(java.util.stream.Collectors.toList())
                    : java.util.Collections.emptyList())
                .build();
    }
}
