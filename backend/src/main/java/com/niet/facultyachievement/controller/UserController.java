package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.UserCreateRequest;
import com.niet.facultyachievement.dto.UserProfileUpdateRequest;
import com.niet.facultyachievement.dto.UserResponse;
import com.niet.facultyachievement.dto.UserStatusUpdateRequest;
import com.niet.facultyachievement.dto.UserUpdateRequest;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.service.AuditLogService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final UserManagementService userManagementService;

    /**
     * PUT /api/users/me — Faculty self-profile update.
     * User identity derived from JWT (no client-controlled user ID).
     * Only fullName, phone, designation are applied from the DTO.
     * Role, department, status, employeeId, email, passwordHash are NEVER modified.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request) {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        // Only update allowed fields — ignore everything else
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            user.setDesignation(request.getDesignation().trim());
        }

        User saved = userRepository.save(user);

        // Audit profile update
        auditLogService.logAction(
                AuditAction.PROFILE_UPDATED,
                "USER",
                saved.getId(),
                "Faculty member updated profile information",
                saved,
                null
        );

        return ResponseEntity.ok(UserResponse.fromEntity(saved));
    }

    /**
     * GET /api/users — full institutional roster.
     *
     * <p>Administrators keep the access they have always had. The user-management
     * permissions are accepted as well, because every one of them is impossible
     * to use without being able to find the person first: you cannot choose whose
     * permissions to change, whose details to correct or whose account to
     * deactivate from a list you are not allowed to read. The ability is inherent
     * to those permissions, not extra reach.
     *
     * <p>Note this is an ADDITION, not a replacement: the original
     * {@code hasRole('ADMIN')} clause is untouched, so no existing behaviour
     * changes. {@code UserResponse} never includes a password hash.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')"
            + " or hasAuthority('" + Permissions.MANAGE_PERMISSIONS + "')"
            + " or hasAuthority('" + Permissions.CREATE_FACULTY + "')"
            + " or hasAuthority('" + Permissions.CREATE_HOD + "')"
            + " or hasAuthority('" + Permissions.EDIT_FACULTY + "')"
            + " or hasAuthority('" + Permissions.EDIT_HOD + "')"
            + " or hasAuthority('" + Permissions.MANAGE_USER_STATUS + "')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/users/{id} — view a single user's detail.
     * Same additive rule as the roster above; it is what the edit form loads.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')"
            + " or hasAuthority('" + Permissions.MANAGE_PERMISSIONS + "')"
            + " or hasAuthority('" + Permissions.CREATE_FACULTY + "')"
            + " or hasAuthority('" + Permissions.CREATE_HOD + "')"
            + " or hasAuthority('" + Permissions.EDIT_FACULTY + "')"
            + " or hasAuthority('" + Permissions.EDIT_HOD + "')"
            + " or hasAuthority('" + Permissions.MANAGE_USER_STATUS + "')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    /**
     * GET /api/users/department — HOD-only: faculty in HOD's own department.
     * Department derived from authenticated user's JWT, NOT from query parameter.
     */
    @GetMapping("/department")
    @PreAuthorize("hasRole('HOD')")
    public ResponseEntity<List<UserResponse>> getDepartmentFaculty(Authentication authentication) {
        String email = authentication.getName();
        User hodUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated HOD not found"));

        if (hodUser.getDepartment() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<UserResponse> users = userRepository.findByDepartmentId(hodUser.getDepartment().getId())
                .stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // ------------------------------------------------------------------------
    // Administrative user management
    //
    // The annotations below are a coarse front door only: "does this caller hold
    // at least one of the permissions that could possibly allow this?". The exact
    // rule depends on the request body and on the target account — which role is
    // being created, whose account is being edited — so it is decided in
    // UserManagementServiceImpl where both are known. Never rely on the
    // annotation alone to read the whole rule.
    //
    // The acting administrator always comes from Authentication (the JWT), never
    // from the request body.
    // ------------------------------------------------------------------------

    /**
     * POST /api/users — create a new account.
     *
     * <p>Which role the caller may create is enforced in the service:
     * CREATE_FACULTY creates only Faculty, CREATE_HOD only Heads of Department,
     * and an Administrator account additionally requires the caller to be an
     * administrator themselves.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')"
            + " or hasAuthority('" + Permissions.CREATE_FACULTY + "')"
            + " or hasAuthority('" + Permissions.CREATE_HOD + "')"
            + " or hasAuthority('" + Permissions.CREATE_ADMIN + "')")
    public ResponseEntity<UserResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody UserCreateRequest request) {

        UserResponse created = userManagementService.createUser(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/users/{id} — edit an existing account.
     *
     * <p>Separate from {@code PUT /api/users/me}: that one is self-service and can
     * only touch name, phone and designation. This one can also change email,
     * employee ID, department, role and password, so it is permission-gated and
     * refuses to let anyone change their own role or department.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')"
            + " or hasAuthority('" + Permissions.EDIT_FACULTY + "')"
            + " or hasAuthority('" + Permissions.EDIT_HOD + "')")
    public ResponseEntity<UserResponse> updateUser(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody UserUpdateRequest request) {

        UserResponse updated = userManagementService.updateUser(id, request, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/users/{id}/status — activate, deactivate or suspend an account.
     *
     * <p>Takes effect immediately: {@code CustomUserDetailsService} rebuilds the
     * principal on every request, so a deactivated user is refused on their very
     * next call even though their token has not expired.
     *
     * <p>Refuses with 409 if it would leave the portal with no active
     * administrator.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_USER_STATUS + "')")
    public ResponseEntity<UserResponse> updateUserStatus(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {

        UserResponse updated = userManagementService.updateUserStatus(id, request, authentication.getName());
        return ResponseEntity.ok(updated);
    }
}
