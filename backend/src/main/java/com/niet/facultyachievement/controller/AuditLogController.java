package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.AuditLogResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.security.UserPermissionResolver;
import com.niet.facultyachievement.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST Controller for Institutional Audit Logs.
 * Base Endpoint: /api/audit-logs
 *
 * RESTRICTED: ROLE_ADMIN, or any account explicitly granted VIEW_AUDIT_LOGS.
 * Everyone else is blocked from institutional audit logs (403 Forbidden).
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final UserPermissionResolver userPermissionResolver;

    /**
     * Confirms the caller may read the audit trail, and returns them.
     *
     * <p>Written as a Java check rather than {@code @PreAuthorize} because that is
     * how this controller has always worked; the VIEW_AUDIT_LOGS permission is
     * added here as an ALTERNATIVE to the administrator role, never as a
     * replacement, so every administrator keeps exactly the access they had.
     *
     * <p>Note that the URL rule in {@code SecurityConfig} has to allow this
     * permission too. Spring's URL-level rules run first, so if that rule still
     * said "administrators only" this method would never be reached and the
     * permission would silently do nothing.
     */
    private User getAuthenticatedAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User is not authenticated");
        }
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user record not found"));

        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN");

        // Read from the database, not from the token, so revoking the permission
        // takes effect on the very next request.
        boolean hasPermission = userPermissionResolver.resolvePermissionCodes(currentUser)
                .contains(Permissions.VIEW_AUDIT_LOGS);

        if (!isAdmin && !hasPermission) {
            throw new AccessDeniedException("Access denied. Institutional audit log access requires "
                    + "Administrator privileges or the VIEW_AUDIT_LOGS permission.");
        }
        return currentUser;
    }

    /**
     * GET /api/audit-logs
     * Server-side paginated search for institutional audit records. Restricted to ROLE_ADMIN.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<AuditLogResponse>> getAuditLogs(
            Authentication authentication,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        // Enforce ADMIN role / VIEW_AUDIT_LOGS permission security check
        getAuthenticatedAdmin(authentication);

        AuditAction auditAction = null;
        if (action != null && !action.isBlank()) {
            try {
                auditAction = AuditAction.valueOf(action.toUpperCase().trim());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid audit action filter value: '" + action + "'");
            }
        }

        PagedResponse<AuditLogResponse> response = auditLogService.searchAuditLogs(
                auditAction, entityType, actorUserId, fromDate, toDate, page, size, sortBy, sortDir
        );
        return ResponseEntity.ok(response);
    }
}
