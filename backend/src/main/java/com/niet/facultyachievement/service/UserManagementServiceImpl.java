package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.UserCreateRequest;
import com.niet.facultyachievement.dto.UserResponse;
import com.niet.facultyachievement.dto.UserStatusUpdateRequest;
import com.niet.facultyachievement.dto.UserUpdateRequest;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.Role;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ConflictException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.RoleRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.security.UserPermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Creates, edits and activates/deactivates portal accounts.
 *
 * <p>Together with {@code PermissionServiceImpl} this is where authority is
 * handed out, so the same defensive style applies: the acting administrator is
 * always re-read from the database, every rule is checked here rather than
 * trusted from the request, and the checks are written so that the *absence* of
 * a permission is what blocks an action.
 *
 * <p>The {@code @PreAuthorize} annotations on the controller are only a coarse
 * front door ("does this caller hold *any* of the create permissions?"). The
 * precise rule — which role you are allowed to create, whose account you may
 * edit — depends on the request body and the target account, so it is decided
 * here where both are available.
 */
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ROLE_FACULTY = "ROLE_FACULTY";
    private static final String ROLE_HOD = "ROLE_HOD";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionResolver userPermissionResolver;
    private final AuditLogService auditLogService;

    // ---------------------------------------------------------------- create

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request, String actorEmail) {

        User actor = loadActor(actorEmail);
        String targetRoleName = normaliseRoleName(request.getRole());

        // Which permission is needed depends entirely on how powerful the new
        // account will be. Someone trusted to add faculty must not be able to
        // add a Head of Department, and only a full administrator may create
        // another administrator.
        requirePermissionToPlaceInRole(actor, targetRoleName);

        UserStatus status = request.getStatus() == null || request.getStatus().isBlank()
                ? UserStatus.ACTIVE
                : parseStatus(request.getStatus());

        // Creating an account that is already switched off is the same decision
        // as switching one off, so it needs the same permission.
        if (status != UserStatus.ACTIVE) {
            requirePermission(actor, Permissions.MANAGE_USER_STATUS,
                    "You do not have permission to create an account in the " + status + " state.");
        }

        String email = normaliseEmail(request.getEmail());
        String employeeId = request.getEmployeeId().trim();

        // Checked before saving so the caller gets a clear 409 explaining which
        // field clashed, rather than a database constraint error.
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with the email '" + email + "' already exists.");
        }
        if (userRepository.existsByEmployeeId(employeeId)) {
            throw new ConflictException("An account with the employee ID '" + employeeId + "' already exists.");
        }

        Department department = loadDepartment(request.getDepartmentId());
        Role role = loadRole(targetRoleName);

        User user = User.builder()
                .employeeId(employeeId)
                .fullName(request.getFullName().trim())
                .email(email)
                // BCrypt via the application's single PasswordEncoder bean. The
                // plain password is used here and nowhere else — it is never
                // stored, returned, logged or audited.
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .designation(request.getDesignation().trim())
                .department(department)
                .role(role)
                .phone(isBlank(request.getPhone()) ? null : request.getPhone().trim())
                .status(status)
                .build();

        User saved = userRepository.save(user);

        auditLogService.logAction(AuditAction.USER_CREATED, "USER", saved.getId(),
                "Created " + roleLabel(targetRoleName) + " account " + saved.getEmail()
                        + " (employee ID " + saved.getEmployeeId() + ", department "
                        + department.getCode() + ", status " + status + ")",
                actor, null);

        return UserResponse.fromEntity(saved);
    }

    // ---------------------------------------------------------------- update

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request, String actorEmail) {

        User actor = loadActor(actorEmail);
        User target = loadTarget(userId);
        boolean isSelf = actor.getId().equals(target.getId());

        // May this actor edit this kind of account at all?
        requirePermissionToEdit(actor, target);

        // Track what actually changed, so the audit entry is specific rather
        // than a vague "user was updated".
        List<String> changes = new ArrayList<>();
        String previousRoleName = currentRoleName(target);
        String newRoleName = null;

        // ---- role change: judged as strictly as creating that role ----------
        if (!isBlank(request.getRole())) {
            String requested = normaliseRoleName(request.getRole());
            if (!requested.equals(previousRoleName)) {
                if (isSelf) {
                    // The most direct escalation there is. Blocked even for a
                    // full administrator, on the same reasoning as the
                    // permissions screen: a compromised account must not be
                    // able to quietly change what it is.
                    throw new AccessDeniedException(
                            "You cannot change your own role. Ask another administrator to do it.");
                }

                // Promotion is gated by the create permission for the new role.
                requirePermissionToPlaceInRole(actor, requested);

                // Demotion out of the administrator role is administrator-only:
                // there is no EDIT_ADMIN permission, and letting a delegated
                // manager strip an administrator would undo every rule above.
                if (ROLE_ADMIN.equals(previousRoleName) && !userPermissionResolver.isAdmin(actor)) {
                    throw new AccessDeniedException(
                            "Only an administrator can change the role of another administrator.");
                }

                if (ROLE_ADMIN.equals(previousRoleName)) {
                    guardLastActiveAdmin(target,
                            "This is the only active administrator. Give another account the "
                                    + "Administrator role first, otherwise nobody would be able to "
                                    + "manage users afterwards.");
                }

                newRoleName = requested;
            }
        }

        // ---- identity fields -------------------------------------------------
        if (!isBlank(request.getEmail())) {
            String email = normaliseEmail(request.getEmail());
            if (!email.equalsIgnoreCase(target.getEmail())) {
                if (userRepository.existsByEmail(email)) {
                    throw new ConflictException("An account with the email '" + email + "' already exists.");
                }
                target.setEmail(email);
                changes.add("email");
            }
        }

        if (!isBlank(request.getEmployeeId())) {
            String employeeId = request.getEmployeeId().trim();
            if (!employeeId.equals(target.getEmployeeId())) {
                if (userRepository.existsByEmployeeId(employeeId)) {
                    throw new ConflictException(
                            "An account with the employee ID '" + employeeId + "' already exists.");
                }
                target.setEmployeeId(employeeId);
                changes.add("employee ID");
            }
        }

        // ---- plain descriptive fields ---------------------------------------
        if (!isBlank(request.getFullName()) && !request.getFullName().trim().equals(target.getFullName())) {
            target.setFullName(request.getFullName().trim());
            changes.add("full name");
        }
        if (!isBlank(request.getDesignation())
                && !request.getDesignation().trim().equals(target.getDesignation())) {
            target.setDesignation(request.getDesignation().trim());
            changes.add("designation");
        }
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            String applied = phone.isEmpty() ? null : phone;
            if (applied == null ? target.getPhone() != null : !applied.equals(target.getPhone())) {
                target.setPhone(applied);
                changes.add("phone");
            }
        }

        // ---- department ------------------------------------------------------
        if (request.getDepartmentId() != null
                && (target.getDepartment() == null
                    || !request.getDepartmentId().equals(target.getDepartment().getId()))) {

            // Moving yourself between departments is an escalation for a Head of
            // Department: their verification rights and dashboards are scoped by
            // department, so they would gain authority over a different set of
            // faculty.
            if (isSelf) {
                throw new AccessDeniedException(
                        "You cannot move your own account to a different department. "
                                + "Ask another administrator to do it.");
            }
            Department department = loadDepartment(request.getDepartmentId());
            target.setDepartment(department);
            changes.add("department (now " + department.getCode() + ")");
        }

        // ---- password reset --------------------------------------------------
        boolean passwordReset = false;
        if (!isBlank(request.getNewPassword())) {
            target.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            passwordReset = true;
        }

        if (newRoleName != null) {
            target.setRole(loadRole(newRoleName));
        }

        // Nothing to do — say so plainly instead of writing a misleading audit
        // entry claiming the account was changed.
        if (changes.isEmpty() && newRoleName == null && !passwordReset) {
            return UserResponse.fromEntity(target);
        }

        User saved = userRepository.save(target);

        if (newRoleName != null) {
            auditLogService.logAction(AuditAction.ROLE_CHANGED, "USER", saved.getId(),
                    "Changed role of " + saved.getEmail()
                            + " from " + roleLabel(previousRoleName) + " to " + roleLabel(newRoleName),
                    actor, null);
        }
        if (passwordReset) {
            // The fact of the reset only. The password and its hash are never
            // written to the audit trail or the application log.
            auditLogService.logAction(AuditAction.PASSWORD_RESET, "USER", saved.getId(),
                    "Reset the password for " + saved.getEmail(), actor, null);
        }
        if (!changes.isEmpty()) {
            auditLogService.logAction(AuditAction.USER_UPDATED, "USER", saved.getId(),
                    "Updated " + saved.getEmail() + " — changed: " + String.join(", ", changes),
                    actor, null);
        }

        return UserResponse.fromEntity(saved);
    }

    // ---------------------------------------------------------------- status

    @Override
    @Transactional
    public UserResponse updateUserStatus(Long userId, UserStatusUpdateRequest request, String actorEmail) {

        User actor = loadActor(actorEmail);
        User target = loadTarget(userId);

        requirePermission(actor, Permissions.MANAGE_USER_STATUS,
                "You do not have permission to activate or deactivate accounts.");

        // Locking yourself out is almost always a mistake, and for the last
        // administrator it would be unrecoverable.
        if (actor.getId().equals(target.getId())) {
            throw new AccessDeniedException("You cannot change the status of your own account.");
        }

        // A delegated status manager must not be able to switch off the people
        // who supervise them.
        if (userPermissionResolver.isAdmin(target) && !userPermissionResolver.isAdmin(actor)) {
            throw new AccessDeniedException(
                    "Only an administrator can change the status of another administrator.");
        }

        UserStatus newStatus = parseStatus(request.getStatus());
        UserStatus previousStatus = target.getStatus();

        if (newStatus == previousStatus) {
            return UserResponse.fromEntity(target);
        }

        if (newStatus != UserStatus.ACTIVE) {
            guardLastActiveAdmin(target,
                    "This is the only active administrator. Deactivating it would leave nobody "
                            + "able to manage users or restore access.");
        }

        target.setStatus(newStatus);
        User saved = userRepository.save(target);

        String reason = isBlank(request.getReason()) ? null : request.getReason().trim();
        auditLogService.logAction(AuditAction.USER_STATUS_CHANGED, "USER", saved.getId(),
                "Changed status of " + saved.getEmail() + " from " + previousStatus + " to " + newStatus
                        + (reason == null ? "" : " — reason: " + reason),
                actor, null);

        return UserResponse.fromEntity(saved);
    }

    // ------------------------------------------------------------ guard rules

    /**
     * Refuses to leave the portal with no active administrator.
     *
     * <p>Only applies when the account in question is an administrator who is
     * active right now — deactivating an already-inactive administrator changes
     * nothing, so it is allowed. The count deliberately excludes the target
     * instead of subtracting one afterwards, which would give the wrong answer
     * for an account that was already inactive.
     */
    private void guardLastActiveAdmin(User target, String message) {
        if (!userPermissionResolver.isAdmin(target) || target.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        Role adminRole = target.getRole();
        long remaining = userRepository.countByRoleAndStatusExcludingUser(
                adminRole.getId(), UserStatus.ACTIVE, target.getId());
        if (remaining == 0) {
            throw new ConflictException(message);
        }
    }

    /**
     * The permission needed to put an account into a given role, whether by
     * creating it there or by moving it there later. Both are the same decision:
     * afterwards, somebody holds that role.
     */
    private void requirePermissionToPlaceInRole(User actor, String roleName) {
        switch (roleName) {
            case ROLE_FACULTY -> requirePermission(actor, Permissions.CREATE_FACULTY,
                    "You do not have permission to create or promote Faculty accounts.");
            case ROLE_HOD -> requirePermission(actor, Permissions.CREATE_HOD,
                    "You do not have permission to create or promote Head of Department accounts.");
            case ROLE_ADMIN -> {
                // Two conditions, not one. CREATE_ADMIN is already restricted so
                // that only an administrator can grant it, but checking the
                // actor's own role here as well means that even if a grant rule
                // were ever relaxed by mistake, a non-administrator still could
                // not mint an account that implicitly holds every permission.
                requirePermission(actor, Permissions.CREATE_ADMIN,
                        "You do not have permission to create Administrator accounts.");
                if (!userPermissionResolver.isAdmin(actor)) {
                    throw new AccessDeniedException(
                            "Only an administrator can create another Administrator account.");
                }
            }
            default -> throw new BadRequestException("Unknown role: " + roleName);
        }
    }

    /**
     * The permission needed to edit an existing account, chosen by what that
     * account is today. Administrators can only be edited by administrators,
     * because the permission catalogue has no EDIT_ADMIN code — an account
     * trusted to correct faculty details is not thereby trusted to change an
     * administrator's email address or password.
     */
    private void requirePermissionToEdit(User actor, User target) {
        String targetRole = currentRoleName(target);
        switch (targetRole) {
            case ROLE_FACULTY -> requirePermission(actor, Permissions.EDIT_FACULTY,
                    "You do not have permission to edit Faculty accounts.");
            case ROLE_HOD -> requirePermission(actor, Permissions.EDIT_HOD,
                    "You do not have permission to edit Head of Department accounts.");
            case ROLE_ADMIN -> {
                if (!userPermissionResolver.isAdmin(actor)) {
                    throw new AccessDeniedException(
                            "Only an administrator can edit another Administrator account.");
                }
            }
            default -> throw new BadRequestException(
                    "This account has an unrecognised role (" + targetRole + ") and cannot be edited here.");
        }
    }

    /**
     * The single place a permission is tested. Resolved from the database each
     * time — an administrator implicitly holds everything, everyone else holds
     * exactly what has been granted to them, and nothing is ever read from the
     * request.
     */
    private void requirePermission(User actor, String code, String message) {
        Set<String> codes = userPermissionResolver.resolvePermissionCodes(actor);
        if (!codes.contains(code)) {
            throw new AccessDeniedException(message);
        }
    }

    // -------------------------------------------------------------- utilities

    private User loadActor(String actorEmail) {
        return userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private User loadTarget(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private Department loadDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new BadRequestException("Department not found with id: " + departmentId));
    }

    private Role loadRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "Role '" + roleName + "' is missing from the roles table. "
                                + "Check that migration V2__seed_reference_data.sql has been applied."));
    }

    /**
     * Accepts "FACULTY" and "ROLE_FACULTY" alike, because the frontend and the
     * database have historically used both spellings, and rejects anything else
     * rather than defaulting to a role the caller did not ask for.
     */
    private String normaliseRoleName(String raw) {
        if (isBlank(raw)) {
            throw new BadRequestException("Role is required.");
        }
        String name = raw.trim().toUpperCase();
        if (!name.startsWith(ROLE_PREFIX)) {
            name = ROLE_PREFIX + name;
        }
        if (!ROLE_FACULTY.equals(name) && !ROLE_HOD.equals(name) && !ROLE_ADMIN.equals(name)) {
            throw new BadRequestException(
                    "Unknown role '" + raw + "'. Allowed values: ROLE_FACULTY, ROLE_HOD, ROLE_ADMIN.");
        }
        return name;
    }

    private String currentRoleName(User user) {
        if (user.getRole() == null || user.getRole().getName() == null) {
            return "";
        }
        String name = user.getRole().getName().trim().toUpperCase();
        return name.startsWith(ROLE_PREFIX) ? name : ROLE_PREFIX + name;
    }

    private UserStatus parseStatus(String raw) {
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Unknown status '" + raw + "'. Allowed values: ACTIVE, INACTIVE, SUSPENDED.");
        }
    }

    /** Emails are stored trimmed and lower-cased so the same address cannot be registered twice. */
    private String normaliseEmail(String raw) {
        return raw.trim().toLowerCase();
    }

    private String roleLabel(String roleName) {
        return switch (roleName) {
            case ROLE_ADMIN -> "Administrator";
            case ROLE_HOD -> "Head of Department";
            case ROLE_FACULTY -> "Faculty";
            default -> roleName;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
