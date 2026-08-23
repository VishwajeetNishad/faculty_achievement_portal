-- ====================================================================
-- V3: Fine-grained user permissions (Track A)
--
-- WHY THIS EXISTS
-- The portal already has three roles (ROLE_ADMIN, ROLE_HOD, ROLE_FACULTY).
-- Roles answer the broad question "what kind of user is this?". They cannot
-- answer the narrower question "may THIS user create faculty accounts?"
-- without inventing more and more roles.
--
-- So permissions are ADDED ALONGSIDE roles, never instead of them:
--   role        -> the user's job (unchanged, still enforced everywhere)
--   permissions -> extra abilities granted to one specific user
--
-- Example: a Head of Department keeps ROLE_HOD, and an Admin additionally
-- grants them CREATE_FACULTY so they can onboard their own staff.
--
-- IMPORTANT: this migration does NOT touch the users or roles tables. No
-- existing user's role changes. A user with no rows in user_permissions
-- behaves exactly as they do today.
--
-- ROLE_ADMIN is deliberately NOT given rows here. Admins are treated in code
-- (CustomUserDetailsService) as implicitly holding every permission. That
-- avoids a chicken-and-egg problem where nobody can grant the first
-- permission, and guarantees an Admin can never be locked out of their own
-- system by an incomplete grant table.
-- ====================================================================

-- --------------------------------------------------------------------
-- The catalogue of permissions the application understands.
-- permission_code is the value that becomes a Spring Security authority.
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permissions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `permission_code` VARCHAR(50) NOT NULL UNIQUE,
    `description` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------
-- Which user has which permission.
--
-- UNIQUE (user_id, permission_id) makes a duplicate grant impossible at the
-- database level, so the API can never accidentally grant the same
-- permission twice.
--
-- ON DELETE CASCADE on both sides: deleting a user removes their grants,
-- and removing a permission from the catalogue removes it from every user.
-- Neither leaves orphaned authority rows behind.
--
-- Who granted what and when is recorded in the existing audit_logs table,
-- so it is not duplicated here.
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_permissions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `granted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_user_permissions_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_permissions_permission`
        FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `uk_user_permission` UNIQUE (`user_id`, `permission_id`),
    INDEX `idx_user_permissions_user` (`user_id`),
    INDEX `idx_user_permissions_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------
-- Seed the 15 permission codes.
--
-- Explicit IDs keep foreign keys predictable across environments, and
-- ON DUPLICATE KEY UPDATE makes this safe to re-run (same pattern as
-- V2__seed_reference_data.sql). Descriptions are what the Admin sees in the
-- "Manage Permissions" screen, so they are written in plain language.
-- --------------------------------------------------------------------
INSERT INTO `permissions` (`id`, `permission_code`, `description`) VALUES
-- User management
(1,  'CREATE_FACULTY',      'Create new faculty accounts'),
(2,  'EDIT_FACULTY',        'Edit existing faculty account details'),
(3,  'CREATE_HOD',          'Create new Head of Department accounts'),
(4,  'EDIT_HOD',            'Edit existing Head of Department account details'),
(5,  'CREATE_ADMIN',        'Create new administrator accounts (highly restricted)'),
(6,  'MANAGE_USER_STATUS',  'Activate, deactivate or suspend user accounts'),
-- Achievement management
(7,  'VIEW_ALL_ACHIEVEMENTS', 'View achievements belonging to every department'),
(8,  'VERIFY_ACHIEVEMENT',  'Approve or reject submitted achievements'),
(9,  'EDIT_ACHIEVEMENT',    'Edit achievement records'),
(10, 'DELETE_ACHIEVEMENT',  'Delete achievement records'),
-- Reporting
(11, 'VIEW_REPORTS',        'View institutional reports and analytics'),
(12, 'EXPORT_REPORTS',      'Export report data to CSV'),
-- System administration
(13, 'MANAGE_DEPARTMENTS',  'Create, edit and remove departments'),
(14, 'VIEW_AUDIT_LOGS',     'View the system audit trail'),
(15, 'MANAGE_PERMISSIONS',  'Grant and revoke permissions for other users (highly restricted)')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
