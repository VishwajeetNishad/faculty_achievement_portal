package com.niet.facultyachievement.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The single source of truth for permission codes.
 *
 * <p>WHY A CONSTANTS CLASS AND NOT AN ENUM: Java annotations such as
 * {@code @PreAuthorize("hasAuthority('CREATE_FACULTY')")} can only contain
 * compile-time constant expressions. An enum value cannot be used inside an
 * annotation string, but a {@code static final String} can be joined into one:
 *
 * <pre>
 *   &#64;PreAuthorize("hasAuthority('" + Permissions.CREATE_FACULTY + "')")
 * </pre>
 *
 * <p>That keeps every controller referring to this file instead of repeating
 * loose string literals, so a typo becomes a compile error rather than a
 * silently unenforced security rule.
 *
 * <p>These codes must exactly match the {@code permission_code} values seeded
 * by {@code V3__permissions.sql}. {@link #ALL} is compared against the database
 * at startup by {@code PermissionCatalogValidator}, which logs a clear error if
 * the two have drifted apart.
 */
public final class Permissions {

    // ---- User management ------------------------------------------------
    public static final String CREATE_FACULTY = "CREATE_FACULTY";
    public static final String EDIT_FACULTY = "EDIT_FACULTY";
    public static final String CREATE_HOD = "CREATE_HOD";
    public static final String EDIT_HOD = "EDIT_HOD";
    public static final String CREATE_ADMIN = "CREATE_ADMIN";
    public static final String MANAGE_USER_STATUS = "MANAGE_USER_STATUS";

    // ---- Achievement management -----------------------------------------
    public static final String VIEW_ALL_ACHIEVEMENTS = "VIEW_ALL_ACHIEVEMENTS";
    public static final String VERIFY_ACHIEVEMENT = "VERIFY_ACHIEVEMENT";
    public static final String EDIT_ACHIEVEMENT = "EDIT_ACHIEVEMENT";
    public static final String DELETE_ACHIEVEMENT = "DELETE_ACHIEVEMENT";

    // ---- Reporting -------------------------------------------------------
    public static final String VIEW_REPORTS = "VIEW_REPORTS";
    public static final String EXPORT_REPORTS = "EXPORT_REPORTS";

    // ---- System administration ------------------------------------------
    public static final String MANAGE_DEPARTMENTS = "MANAGE_DEPARTMENTS";
    public static final String VIEW_AUDIT_LOGS = "VIEW_AUDIT_LOGS";
    public static final String MANAGE_PERMISSIONS = "MANAGE_PERMISSIONS";

    /**
     * Every permission code the application understands, in display order.
     *
     * <p>A LinkedHashSet wrapped in unmodifiableSet is used rather than
     * {@code Set.of(...)}: both are read-only, but only LinkedHashSet keeps a
     * predictable iteration order, so the Admin permission screen always
     * renders the checkboxes in the same grouped sequence.
     */
    public static final Set<String> ALL = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            CREATE_FACULTY,
            EDIT_FACULTY,
            CREATE_HOD,
            EDIT_HOD,
            CREATE_ADMIN,
            MANAGE_USER_STATUS,
            VIEW_ALL_ACHIEVEMENTS,
            VERIFY_ACHIEVEMENT,
            EDIT_ACHIEVEMENT,
            DELETE_ACHIEVEMENT,
            VIEW_REPORTS,
            EXPORT_REPORTS,
            MANAGE_DEPARTMENTS,
            VIEW_AUDIT_LOGS,
            MANAGE_PERMISSIONS
    )));

    /**
     * Permissions that let their holder hand out further power, so they must
     * only ever be granted by a full administrator.
     *
     * <p>MANAGE_PERMISSIONS is the master key — anyone holding it can grant
     * themselves anything else. CREATE_ADMIN creates accounts that implicitly
     * hold every permission. Both are checked separately in
     * {@code PermissionServiceImpl}.
     */
    public static final Set<String> ADMIN_ONLY_GRANTABLE = Set.of(
            MANAGE_PERMISSIONS,
            CREATE_ADMIN
    );

    private Permissions() {
        // Utility class — never instantiated.
    }
}
