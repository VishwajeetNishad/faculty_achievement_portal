package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.PermissionResponse;
import com.niet.facultyachievement.dto.UserPermissionsResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.Permission;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserPermission;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.PermissionRepository;
import com.niet.facultyachievement.repository.UserPermissionRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.security.UserPermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Grants and revokes individual permissions.
 *
 * <p>This is the most security-sensitive service in the application: it is the
 * one place where power is handed out. Every rule below exists to stop the
 * system being talked into giving someone more authority than the caller has,
 * and to stop it ever locking every administrator out.
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;
    private final UserPermissionResolver userPermissionResolver;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getId))
                .map(PermissionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserPermissionsResponse getUserPermissions(Long userId) {
        User user = findUserOrThrow(userId);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserPermissionsResponse updateUserPermissions(Long userId, List<String> requestedCodes, String actorEmail) {

        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        User target = findUserOrThrow(userId);

        // ---- Guard 1: nobody edits their own permissions -------------------
        // The most direct escalation route is simply ticking more boxes on your
        // own account. Even a full administrator is blocked here: it costs them
        // nothing (they already hold everything) and it means a compromised
        // account cannot quietly widen its own reach.
        if (actor.getId().equals(target.getId())) {
            throw new AccessDeniedException("You cannot change your own permissions. Ask another administrator.");
        }

        // ---- Guard 2: administrators are not managed through this table -----
        // An administrator already holds every permission by virtue of their
        // role, so writing grant rows for them would be misleading: the UI
        // would imply the ticks are what give them access, when removing them
        // would change nothing.
        if (userPermissionResolver.isAdmin(target)) {
            throw new BadRequestException(
                    "This user is an administrator and already holds every permission. "
                            + "Individual permissions only apply to Head of Department and Faculty accounts.");
        }

        Set<String> newCodes = normalise(requestedCodes);

        // ---- Guard 3: every code must be one the system actually knows ------
        // An unknown code is rejected outright rather than quietly dropped, so
        // a typo in the UI can never look like a successful save.
        Set<String> unknown = new TreeSet<>(newCodes);
        unknown.removeAll(Permissions.ALL);
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Unknown permission code(s): " + String.join(", ", unknown));
        }

        Set<String> currentCodes = new LinkedHashSet<>(
                userPermissionRepository.findPermissionCodesByUserId(target.getId()));

        // Work out what is actually changing. Only the difference is checked and
        // audited, so re-saving an unchanged form is a harmless no-op.
        Set<String> toGrant = new LinkedHashSet<>(newCodes);
        toGrant.removeAll(currentCodes);

        Set<String> toRevoke = new LinkedHashSet<>(currentCodes);
        toRevoke.removeAll(newCodes);

        if (toGrant.isEmpty() && toRevoke.isEmpty()) {
            return toResponse(target);
        }

        boolean actorIsAdmin = userPermissionResolver.isAdmin(actor);
        Set<String> actorCodes = userPermissionResolver.resolvePermissionCodes(actor);

        // Both directions are checked, not just granting. If revoking were
        // unchecked, a delegated manager could strip permissions an
        // administrator had deliberately given someone — including taking away
        // the ability to manage permissions at all.
        Set<String> changing = new LinkedHashSet<>(toGrant);
        changing.addAll(toRevoke);

        for (String code : changing) {
            // ---- Guard 4: the two master keys are administrator-only --------
            // MANAGE_PERMISSIONS lets its holder grant themselves anything
            // else, and CREATE_ADMIN creates accounts that implicitly hold
            // everything. Handing either to a non-administrator would make the
            // rest of these checks pointless.
            if (Permissions.ADMIN_ONLY_GRANTABLE.contains(code) && !actorIsAdmin) {
                throw new AccessDeniedException(
                        "Only an administrator can grant or revoke the '" + code + "' permission.");
            }

            // ---- Guard 5: you cannot pass on power you do not have ----------
            // Without this, a Head of Department who had been given
            // MANAGE_PERMISSIONS could hand out CREATE_HOD or VIEW_AUDIT_LOGS
            // — permissions they were never trusted with themselves.
            if (!actorCodes.contains(code)) {
                throw new AccessDeniedException(
                        "You cannot grant or revoke the '" + code + "' permission because you do not hold it yourself.");
            }
        }

        applyChanges(target, toGrant, toRevoke);

        // ---- Audit ---------------------------------------------------------
        // One line per individual change so the trail answers "who gave this
        // person that power, and when?", plus a single summary line for the
        // whole save. Only codes and the target's email are recorded — never a
        // password, hash or token.
        for (String code : toGrant) {
            auditLogService.logAction(AuditAction.PERMISSION_GRANTED, "USER", target.getId(),
                    "Granted permission '" + code + "' to " + target.getEmail(), actor, null);
        }
        for (String code : toRevoke) {
            auditLogService.logAction(AuditAction.PERMISSION_REVOKED, "USER", target.getId(),
                    "Revoked permission '" + code + "' from " + target.getEmail(), actor, null);
        }
        auditLogService.logAction(AuditAction.PERMISSIONS_UPDATED, "USER", target.getId(),
                "Updated permissions for " + target.getEmail()
                        + " — granted: " + describe(toGrant)
                        + "; revoked: " + describe(toRevoke),
                actor, null);

        return toResponse(target);
    }

    /**
     * Writes the grant rows. Revocations are deleted first so that a permission
     * being removed and re-added in the same request cannot collide with the
     * database's UNIQUE (user_id, permission_id) constraint.
     */
    private void applyChanges(User target, Set<String> toGrant, Set<String> toRevoke) {
        if (!toRevoke.isEmpty()) {
            List<UserPermission> existing = userPermissionRepository.findByUserId(target.getId());
            List<UserPermission> doomed = existing.stream()
                    .filter(up -> up.getPermission() != null
                            && toRevoke.contains(up.getPermission().getPermissionCode()))
                    .collect(Collectors.toList());
            userPermissionRepository.deleteAll(doomed);
            userPermissionRepository.flush();
        }

        if (!toGrant.isEmpty()) {
            List<Permission> permissions = permissionRepository.findByPermissionCodeIn(toGrant);

            // A code that passed the Permissions.ALL check but is missing from
            // the table means the seed migration did not run. Fail loudly rather
            // than silently saving fewer permissions than the admin ticked.
            if (permissions.size() != toGrant.size()) {
                Set<String> found = permissions.stream()
                        .map(Permission::getPermissionCode)
                        .collect(Collectors.toSet());
                Set<String> missing = new TreeSet<>(toGrant);
                missing.removeAll(found);
                throw new IllegalStateException(
                        "Permission catalogue is incomplete — missing rows for: " + String.join(", ", missing)
                                + ". Check that migration V3__permissions.sql has been applied.");
            }

            List<UserPermission> grants = new ArrayList<>();
            for (Permission permission : permissions) {
                grants.add(UserPermission.builder()
                        .user(target)
                        .permission(permission)
                        .build());
            }
            userPermissionRepository.saveAll(grants);
        }
    }

    private UserPermissionsResponse toResponse(User user) {
        boolean isAdmin = userPermissionResolver.isAdmin(user);
        Set<String> codes = userPermissionResolver.resolvePermissionCodes(user);

        return UserPermissionsResponse.builder()
                .userId(user.getId())
                .employeeId(user.getEmployeeId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .departmentCode(user.getDepartment() != null ? user.getDepartment().getCode() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .permissionCodes(orderForDisplay(codes))
                .allFromRole(isAdmin)
                .build();
    }

    /**
     * Returns codes in the fixed order declared in {@link Permissions#ALL} so
     * the API response order never depends on how rows happened to be inserted.
     */
    private List<String> orderForDisplay(Set<String> codes) {
        return Permissions.ALL.stream()
                .filter(codes::contains)
                .collect(Collectors.toList());
    }

    private Set<String> normalise(List<String> requested) {
        if (requested == null) {
            return new LinkedHashSet<>();
        }
        // Trim and upper-case so trailing whitespace or lower case from the
        // client cannot produce a "code not found" error, and de-duplicate so a
        // repeated code in the request cannot break the UNIQUE constraint.
        return requested.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String describe(Set<String> codes) {
        return codes.isEmpty() ? "none" : String.join(", ", codes);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
