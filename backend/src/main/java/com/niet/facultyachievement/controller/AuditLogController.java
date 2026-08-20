package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.AuditLogResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
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
 * RESTRICTED: ROLE_ADMIN only.
 * Faculty members and HODs are blocked from accessing institutional audit logs (403 Forbidden).
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    private User getAuthenticatedAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User is not authenticated");
        }
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user record not found"));

        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        if (!roleName.equalsIgnoreCase("ADMIN") && !roleName.equalsIgnoreCase("ROLE_ADMIN")) {
            throw new AccessDeniedException("Access denied. Institutional audit log access requires Administrator privileges.");
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
        // Enforce ADMIN role security check
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
