package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.PermissionResponse;
import com.niet.facultyachievement.dto.UserPermissionsResponse;

import java.util.List;

public interface PermissionService {

    /** The full catalogue of permissions the system understands. */
    List<PermissionResponse> getAllPermissions();

    /** The permissions currently held by one user. */
    UserPermissionsResponse getUserPermissions(Long userId);

    /**
     * Replaces a user's entire permission set.
     *
     * @param userId         the user being changed (from the URL, never the body)
     * @param permissionCodes the complete set the user should end up with
     * @param actorEmail     the administrator performing the change, taken from
     *                       the verified JWT — never from the request body
     */
    UserPermissionsResponse updateUserPermissions(Long userId, List<String> permissionCodes, String actorEmail);
}
