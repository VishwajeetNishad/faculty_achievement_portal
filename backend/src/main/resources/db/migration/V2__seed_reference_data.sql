-- ====================================================================
-- V2: Reference / lookup seed data (idempotent)
--
-- These rows are the fixed lookup values the application depends on
-- (roles, departments, achievement categories). Explicit IDs are kept so
-- foreign keys line up predictably. ON DUPLICATE KEY UPDATE makes this
-- migration safe to run against a fresh database or one that already has
-- these rows. The first admin user is created separately by AdminBootstrap
-- (from ADMIN_EMAIL / ADMIN_PASSWORD environment variables).
-- ====================================================================

-- System Roles
INSERT INTO `roles` (`id`, `name`, `description`) VALUES
(1, 'ROLE_ADMIN', 'System Administrator with full management permissions'),
(2, 'ROLE_HOD', 'Head of Department responsible for achievement verification'),
(3, 'ROLE_FACULTY', 'Faculty member who submits and tracks achievements')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- Departments
INSERT INTO `departments` (`id`, `code`, `name`, `description`) VALUES
(1, 'CSE', 'Computer Science & Engineering', 'Department of Computer Science & Engineering'),
(2, 'IT', 'Information Technology', 'Department of Information Technology'),
(3, 'ECE', 'Electronics & Communication Engineering', 'Department of Electronics & Communication Engineering'),
(4, 'EEE', 'Electrical & Electronics Engineering', 'Department of Electrical & Electronics Engineering'),
(5, 'MECH', 'Mechanical Engineering', 'Department of Mechanical Engineering'),
(6, 'CIVIL', 'Civil Engineering', 'Department of Civil Engineering')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

-- Achievement Categories
INSERT INTO `achievement_categories` (`id`, `code`, `category_name`, `description`) VALUES
(1, 'PUBLICATION', 'Research Publication', 'Journal articles, conference papers, books, and book chapters'),
(2, 'PATENT', 'Patent / Intellectual Property', 'Filed, published, or granted patents'),
(3, 'RESEARCH_GRANT', 'Research & Consultancy Grant', 'Funded research projects and industrial consultancy'),
(4, 'WORKSHOP_FDP', 'Workshop / FDP / Certification', 'Faculty Development Programs, workshops, and certifications'),
(5, 'AWARD', 'Award & Recognition', 'Honors and awards received from recognized organizations')
ON DUPLICATE KEY UPDATE `category_name` = VALUES(`category_name`), `description` = VALUES(`description`);
