package com.niet.facultyachievement.config;

import com.niet.facultyachievement.entity.Permission;
import com.niet.facultyachievement.repository.PermissionRepository;
import com.niet.facultyachievement.security.Permissions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Checks at startup that the permission codes in the database match the codes
 * the Java code knows about.
 *
 * <p>WHY THIS EXISTS: permission codes live in two places — the
 * {@link Permissions} constants used by {@code @PreAuthorize} annotations, and
 * the {@code permissions} table seeded by {@code V3__permissions.sql}. If those
 * two ever disagree the symptom is confusing: a checkbox that saves but grants
 * nothing, or a permission nobody can be given. This runner turns that silent
 * drift into an obvious message in the startup log.
 *
 * <p>It only reports; it never changes data and never stops the application
 * from starting. A missing permission row is a configuration problem, not a
 * reason to take the whole portal offline — and
 * {@code PermissionServiceImpl.applyChanges} already refuses to save a partial
 * grant, so nothing can be silently half-applied in the meantime.
 *
 * <p>Runs after Flyway, in the same way {@link AdminBootstrap} does.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionCatalogValidator implements CommandLineRunner {

    private final PermissionRepository permissionRepository;

    @Override
    public void run(String... args) {
        Set<String> inDatabase = permissionRepository.findAll().stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());

        // Codes the application enforces but the database has never heard of.
        // These can never be granted to anyone, so any feature relying on them
        // would appear broken.
        Set<String> missingFromDatabase = new TreeSet<>(Permissions.ALL);
        missingFromDatabase.removeAll(inDatabase);

        // Codes sitting in the database that no annotation checks. Harmless, but
        // usually means a permission was renamed in code and the old row was
        // left behind — it would show up as a checkbox that does nothing.
        Set<String> unknownInDatabase = new TreeSet<>(inDatabase);
        unknownInDatabase.removeAll(Permissions.ALL);

        if (!missingFromDatabase.isEmpty()) {
            log.error("Permission catalogue is incomplete — these codes are used by the application "
                            + "but missing from the 'permissions' table: {}. "
                            + "Check that migration V3__permissions.sql has been applied.",
                    String.join(", ", missingFromDatabase));
        }

        if (!unknownInDatabase.isEmpty()) {
            log.warn("The 'permissions' table contains codes the application does not use: {}. "
                            + "They can be ticked in the Admin screen but will not grant anything.",
                    String.join(", ", unknownInDatabase));
        }

        if (missingFromDatabase.isEmpty() && unknownInDatabase.isEmpty()) {
            log.info("Permission catalogue verified: {} permissions in sync with the database.",
                    Permissions.ALL.size());
        }
    }
}
