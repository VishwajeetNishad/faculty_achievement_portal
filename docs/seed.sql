-- ====================================================================
-- Faculty Achievement Portal - Initial Seed Data Script
-- Target RDBMS: MySQL 8.0+
-- Database: faculty_achievement_db
-- ====================================================================

USE `faculty_achievement_db`;

-- Insert System Roles
INSERT INTO `roles` (`id`, `name`, `description`) VALUES
(1, 'ROLE_ADMIN', 'System Administrator with full management permissions'),
(2, 'ROLE_HOD', 'Head of Department responsible for achievement verification'),
(3, 'ROLE_FACULTY', 'Faculty member who submits and tracks achievements')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

-- Insert Departments
INSERT INTO `departments` (`id`, `code`, `name`, `description`) VALUES
(1, 'CSE', 'Computer Science & Engineering', 'Department of Computer Science & Engineering'),
(2, 'IT', 'Information Technology', 'Department of Information Technology'),
(3, 'ECE', 'Electronics & Communication Engineering', 'Department of Electronics & Communication Engineering'),
(4, 'EEE', 'Electrical & Electronics Engineering', 'Department of Electrical & Electronics Engineering'),
(5, 'MECH', 'Mechanical Engineering', 'Department of Mechanical Engineering'),
(6, 'CIVIL', 'Civil Engineering', 'Department of Civil Engineering')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Insert Achievement Categories
INSERT INTO `achievement_categories` (`id`, `code`, `category_name`, `description`) VALUES
(1, 'PUBLICATION', 'Research Publication', 'Journal articles, conference papers, books, and book chapters'),
(2, 'PATENT', 'Patent / Intellectual Property', 'Filed, published, or granted patents'),
(3, 'RESEARCH_GRANT', 'Research & Consultancy Grant', 'Funded research projects and industrial consultancy'),
(4, 'WORKSHOP_FDP', 'Workshop / FDP / Certification', 'Faculty Development Programs, workshops, and certifications'),
(5, 'AWARD', 'Award & Recognition', 'Honors and awards received from recognized organizations')
ON DUPLICATE KEY UPDATE `category_name` = VALUES(`category_name`);

-- Seed Initial System Administrator (Password: Admin@123 -> BCrypt hash)
INSERT INTO `users` (`id`, `employee_id`, `full_name`, `email`, `password_hash`, `designation`, `department_id`, `role_id`, `phone`, `status`) VALUES
(1, 'EMP001', 'System Administrator', 'admin@faculty.edu', '$2a$10$7R0wK/Q0Qh3j8G5K3y4w0eXp/G.X5gYq2/eU9q2X3Y4Z5W6V7U8T9', 'Administrator', 1, 1, '9876543210', 'ACTIVE')
ON DUPLICATE KEY UPDATE `full_name` = VALUES(`full_name`);
