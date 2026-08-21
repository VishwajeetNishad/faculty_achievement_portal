package com.niet.facultyachievement.config;

import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.Role;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.RoleRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial system administrator on startup from environment
 * variables, so no password (or password hash) is ever committed to source
 * control. This runs after Flyway has created the schema and seeded the
 * reference data (roles, departments), and is idempotent: it does nothing if
 * an admin with the configured email already exists, so restarts are safe.
 *
 * <p>Configure via environment variables (see .env.example):
 * <ul>
 *   <li>{@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} (required to create one)</li>
 *   <li>{@code ADMIN_EMPLOYEE_ID}, {@code ADMIN_FULL_NAME},
 *       {@code ADMIN_DESIGNATION}, {@code ADMIN_DEPARTMENT_CODE},
 *       {@code ADMIN_PHONE} (optional overrides)</li>
 * </ul>
 * If email or password are blank, bootstrap is skipped (useful for local dev
 * where an admin already exists).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private static final String ADMIN_ROLE_NAME = "ROLE_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.employee-id:EMP001}")
    private String adminEmployeeId;

    @Value("${app.bootstrap.admin.full-name:System Administrator}")
    private String adminFullName;

    @Value("${app.bootstrap.admin.designation:Administrator}")
    private String adminDesignation;

    @Value("${app.bootstrap.admin.department-code:CSE}")
    private String adminDepartmentCode;

    @Value("${app.bootstrap.admin.phone:}")
    private String adminPhone;

    @Override
    public void run(String... args) {
        if (isBlank(adminEmail) || isBlank(adminPassword)) {
            log.info("Admin bootstrap skipped: ADMIN_EMAIL / ADMIN_PASSWORD not set.");
            return;
        }

        String email = adminEmail.trim();
        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap skipped: a user with email '{}' already exists.", email);
            return;
        }
        if (userRepository.existsByEmployeeId(adminEmployeeId)) {
            log.warn("Admin bootstrap skipped: employee id '{}' is already taken. "
                    + "Set ADMIN_EMPLOYEE_ID to a free value to create the admin.", adminEmployeeId);
            return;
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME).orElse(null);
        if (adminRole == null) {
            log.error("Admin bootstrap failed: role '{}' not found. Was the V2 seed migration applied?",
                    ADMIN_ROLE_NAME);
            return;
        }

        Department department = departmentRepository.findByCode(adminDepartmentCode)
                .or(() -> departmentRepository.findAll().stream().findFirst())
                .orElse(null);
        if (department == null) {
            log.error("Admin bootstrap failed: no department found (looked for code '{}'). "
                    + "Was the V2 seed migration applied?", adminDepartmentCode);
            return;
        }

        User admin = User.builder()
                .employeeId(adminEmployeeId)
                .fullName(adminFullName)
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .designation(adminDesignation)
                .department(department)
                .role(adminRole)
                .phone(isBlank(adminPhone) ? null : adminPhone.trim())
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);
        // Never log the password — only that the account was created.
        log.info("Admin bootstrap: created initial administrator '{}' (employee id '{}', department '{}').",
                email, adminEmployeeId, department.getCode());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
