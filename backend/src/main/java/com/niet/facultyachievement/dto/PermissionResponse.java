package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.Permission;
import lombok.*;

/**
 * One entry in the permission catalogue, as shown in the Admin
 * "Manage Permissions" screen.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionResponse {

    private Long id;
    private String permissionCode;
    private String description;

    public static PermissionResponse fromEntity(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .permissionCode(permission.getPermissionCode())
                .description(permission.getDescription())
                .build();
    }
}
