package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.PermissionResponse;
import com.niet.facultyachievement.dto.UserPermissionUpdateRequest;
import com.niet.facultyachievement.dto.UserPermissionsResponse;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Permission management endpoints.
 *
 * <p>All three require the MANAGE_PERMISSIONS authority. Administrators hold it
 * implicitly (see {@code UserPermissionResolver}), so no separate
 * {@code hasRole('ADMIN')} clause is needed here.
 *
 * <p>Note that the permission codes are referenced through the
 * {@link Permissions} constants rather than typed as loose strings, so a
 * mistyped permission name fails to compile instead of silently becoming an
 * authority nobody can ever hold.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * GET /api/permissions — the catalogue of permissions that can be granted.
     * Used to render the checkbox list in the Admin screen.
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.MANAGE_PERMISSIONS + "')")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    /**
     * GET /api/users/{userId}/permissions — what this user currently holds.
     */
    @GetMapping("/users/{userId}/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.MANAGE_PERMISSIONS + "')")
    public ResponseEntity<UserPermissionsResponse> getUserPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getUserPermissions(userId));
    }

    /**
     * PUT /api/users/{userId}/permissions — replace this user's permission set.
     *
     * <p>The user being edited comes from the URL and the administrator doing
     * the editing comes from {@code authentication.getName()}, which Spring
     * Security populated from the verified JWT. Neither identity is ever read
     * from the request body, so a caller cannot claim to be someone else.
     */
    @PutMapping("/users/{userId}/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.MANAGE_PERMISSIONS + "')")
    public ResponseEntity<UserPermissionsResponse> updateUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody UserPermissionUpdateRequest request,
            Authentication authentication) {

        UserPermissionsResponse updated = permissionService.updateUserPermissions(
                userId,
                request.getPermissionCodes(),
                authentication.getName()
        );
        return ResponseEntity.ok(updated);
    }
}
