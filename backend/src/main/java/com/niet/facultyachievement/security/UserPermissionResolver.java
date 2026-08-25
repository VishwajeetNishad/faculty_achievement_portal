package com.niet.facultyachievement.security;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out the complete set of permissions a user effectively holds.
 *
 * <p>WHY THIS IS ITS OWN CLASS: the same question — "what can this user
 * actually do?" — is asked in three different places:
 *
 * <ol>
 *   <li>{@code CustomUserDetailsService}, to build Spring Security authorities
 *       on every request;</li>
 *   <li>{@code PermissionServiceImpl}, to stop someone granting a permission
 *       they do not hold themselves;</li>
 *   <li>{@code AuthController#getCurrentUser}, so the frontend knows which
 *       buttons to show.</li>
 * </ol>
 *
 * <p>If the "an administrator implicitly holds everything" rule were copied
 * into all three, one copy could later be changed and the others forgotten —
 * which is exactly how privilege-escalation bugs happen. Keeping it here means
 * there is one rule, in one place.
 */
@Component
@RequiredArgsConstructor
public class UserPermissionResolver {

    /**
     * The role name that implicitly holds every permission. Stored with the
     * {@code ROLE_} prefix in the database (see V2__seed_reference_data.sql).
     */
    public static final String ADMIN_ROLE_NAME = "ROLE_ADMIN";

    private final UserPermissionRepository userPermissionRepository;

    /**
     * The permission codes this user effectively holds.
     *
     * <p>An administrator implicitly holds all of them. This is deliberate:
     *
     * <ul>
     *   <li>It preserves today's behaviour exactly — an admin could already do
     *       everything, and still can.</li>
     *   <li>It avoids a chicken-and-egg problem. If admins needed explicit
     *       grants, the very first admin created by {@code AdminBootstrap}
     *       would have no permission to grant permissions, and the system
     *       would be unusable.</li>
     *   <li>It means the {@code user_permissions} table only ever describes
     *       *extra* power given to a non-admin, which is easier to audit.</li>
     * </ul>
     *
     * <p>Everyone else gets exactly what has been granted to them in the
     * database — never anything sent by the browser.
     */
    @Transactional(readOnly = true)
    public Set<String> resolvePermissionCodes(User user) {
        if (user == null) {
            return Set.of();
        }

        if (isAdmin(user)) {
            return Permissions.ALL;
        }

        // LinkedHashSet: de-duplicates while keeping a stable order for the API response.
        return new LinkedHashSet<>(userPermissionRepository.findPermissionCodesByUserId(user.getId()));
    }

    /**
     * True when this user's role is the administrator role. Compared with the
     * {@code ROLE_} prefix tolerated either way, matching how
     * {@code CustomUserDetailsService} normalises role names.
     */
    public boolean isAdmin(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) {
            return false;
        }
        String roleName = user.getRole().getName();
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }
        return ADMIN_ROLE_NAME.equals(roleName);
    }
}
