-- ====================================================================
-- Faculty Achievement Portal - Database Schema DDL
-- Target RDBMS: MySQL 8.0+
-- Database: faculty_achievement_db
-- ====================================================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS `faculty_achievement_db`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `faculty_achievement_db`;

-- Disable foreign key checks for clean recreation script
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `audit_logs`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `awards`;
DROP TABLE IF EXISTS `workshops_fdps`;
DROP TABLE IF EXISTS `research_grants`;
DROP TABLE IF EXISTS `patents`;
DROP TABLE IF EXISTS `publications`;
DROP TABLE IF EXISTS `achievements`;
DROP TABLE IF EXISTS `achievement_categories`;
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `roles`;
DROP TABLE IF EXISTS `departments`;

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- 2. Core Reference Tables
-- ====================================================================

-- Departments Table
CREATE TABLE `departments` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(20) NOT NULL UNIQUE,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Roles Table
CREATE TABLE `roles` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL UNIQUE,
    `description` VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Users Table (Faculty / HOD / Admin)
CREATE TABLE `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `employee_id` VARCHAR(50) NOT NULL UNIQUE,
    `full_name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `designation` VARCHAR(100) NOT NULL,
    `department_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `phone` VARCHAR(20) NULL,
    `status` ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_users_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE RESTRICT,
    INDEX `idx_users_email` (`email`),
    INDEX `idx_users_department` (`department_id`),
    INDEX `idx_users_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Achievement Categories Table
CREATE TABLE `achievement_categories` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(50) NOT NULL UNIQUE,
    `category_name` VARCHAR(100) NOT NULL,
    `description` TEXT NULL,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- 3. Master Achievement Table
-- ====================================================================

CREATE TABLE `achievements` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `category_id` BIGINT NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `description` TEXT NULL,
    `achievement_date` DATE NOT NULL,
    `academic_year` VARCHAR(20) NOT NULL, -- e.g. "2024-2025"
    `status` ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    `verification_comment` TEXT NULL,
    `verified_by_user_id` BIGINT NULL,
    `verified_at` DATETIME NULL,
    `proof_document_url` VARCHAR(500) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_achievements_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_achievements_category` FOREIGN KEY (`category_id`) REFERENCES `achievement_categories` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_achievements_verifier` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    INDEX `idx_achievements_user` (`user_id`),
    INDEX `idx_achievements_category` (`category_id`),
    INDEX `idx_achievements_academic_year` (`academic_year`),
    INDEX `idx_achievements_status` (`status`),
    INDEX `idx_achievements_date` (`achievement_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- 4. Specialized Achievement Detail Tables
-- ====================================================================

-- Publications Sub-table
CREATE TABLE `publications` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `achievement_id` BIGINT NOT NULL UNIQUE,
    `publication_type` ENUM('JOURNAL', 'CONFERENCE', 'BOOK_CHAPTER', 'BOOK') NOT NULL,
    `journal_conference_name` VARCHAR(255) NOT NULL,
    `publisher` VARCHAR(150) NULL,
    `doi` VARCHAR(100) NULL,
    `isbn_issn` VARCHAR(50) NULL,
    `volume` VARCHAR(20) NULL,
    `issue` VARCHAR(20) NULL,
    `pages` VARCHAR(30) NULL,
    `impact_factor` DECIMAL(5,3) NULL,
    `indexing` ENUM('SCOPUS', 'WEB_OF_SCIENCE', 'UGC_CARE', 'OTHER') NOT NULL DEFAULT 'OTHER',
    CONSTRAINT `fk_publications_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE CASCADE,
    INDEX `idx_publications_indexing` (`indexing`),
    INDEX `idx_publications_type` (`publication_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Patents Sub-table
CREATE TABLE `patents` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `achievement_id` BIGINT NOT NULL UNIQUE,
    `patent_number` VARCHAR(100) NOT NULL,
    `patent_status` ENUM('FILED', 'PUBLISHED', 'GRANTED') NOT NULL,
    `country` VARCHAR(100) NOT NULL DEFAULT 'India',
    `filing_date` DATE NOT NULL,
    `grant_date` DATE NULL,
    CONSTRAINT `fk_patents_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE CASCADE,
    INDEX `idx_patents_status` (`patent_status`),
    INDEX `idx_patents_number` (`patent_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Research Grants Sub-table
CREATE TABLE `research_grants` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `achievement_id` BIGINT NOT NULL UNIQUE,
    `funding_agency` VARCHAR(200) NOT NULL,
    `project_title` VARCHAR(255) NOT NULL,
    `grant_amount` DECIMAL(12,2) NOT NULL,
    `project_type` ENUM('RESEARCH', 'CONSULTANCY', 'INFRASTRUCTURE') NOT NULL DEFAULT 'RESEARCH',
    `duration_months` INT NOT NULL,
    `grant_status` ENUM('SANCTIONED', 'ONGOING', 'COMPLETED') NOT NULL DEFAULT 'SANCTIONED',
    CONSTRAINT `fk_grants_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE CASCADE,
    INDEX `idx_grants_funding_agency` (`funding_agency`),
    INDEX `idx_grants_status` (`grant_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Workshops / FDPs Sub-table
CREATE TABLE `workshops_fdps` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `achievement_id` BIGINT NOT NULL UNIQUE,
    `event_name` VARCHAR(255) NOT NULL,
    `event_type` ENUM('WORKSHOP', 'FDP', 'SEMINAR', 'WEBINAR', 'CERTIFICATION') NOT NULL,
    `role` ENUM('ATTENDED', 'ORGANIZED', 'RESOURCE_PERSON') NOT NULL,
    `location` VARCHAR(200) NULL,
    `duration_days` INT NOT NULL DEFAULT 1,
    `organizing_body` VARCHAR(200) NOT NULL,
    CONSTRAINT `fk_workshops_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE CASCADE,
    INDEX `idx_workshops_event_type` (`event_type`),
    INDEX `idx_workshops_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Awards Sub-table
CREATE TABLE `awards` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `achievement_id` BIGINT NOT NULL UNIQUE,
    `award_name` VARCHAR(255) NOT NULL,
    `awarding_body` VARCHAR(200) NOT NULL,
    `award_level` ENUM('NATIONAL', 'INTERNATIONAL', 'STATE', 'INSTITUTIONAL') NOT NULL,
    CONSTRAINT `fk_awards_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE CASCADE,
    INDEX `idx_awards_level` (`award_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Notifications Table (Step 19)
CREATE TABLE `notifications` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `recipient_id` BIGINT NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `message` TEXT NOT NULL,
    `notification_type` VARCHAR(50) NOT NULL,
    `achievement_id` BIGINT NULL,
    `is_read` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_notifications_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_notifications_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievements` (`id`) ON DELETE SET NULL,
    INDEX `idx_notifications_recipient` (`recipient_id`),
    INDEX `idx_notifications_recipient_read` (`recipient_id`, `is_read`),
    INDEX `idx_notifications_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audit Logs Table (Step 20)
CREATE TABLE `audit_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `actor_user_id` BIGINT NULL,
    `actor_email` VARCHAR(100) NULL,
    `action` VARCHAR(50) NOT NULL,
    `entity_type` VARCHAR(50) NOT NULL,
    `entity_id` BIGINT NULL,
    `description` VARCHAR(500) NULL,
    `ip_address` VARCHAR(45) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_audit_logs_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    INDEX `idx_audit_logs_actor` (`actor_user_id`),
    INDEX `idx_audit_logs_action` (`action`),
    INDEX `idx_audit_logs_entity` (`entity_type`, `entity_id`),
    INDEX `idx_audit_logs_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- 5. Seed Data Insertion
-- ====================================================================

-- Insert System Roles
INSERT INTO `roles` (`id`, `name`, `description`) VALUES
(1, 'ROLE_ADMIN', 'System Administrator with full management permissions'),
(2, 'ROLE_HOD', 'Head of Department responsible for achievement verification'),
(3, 'ROLE_FACULTY', 'Faculty member who submits and tracks achievements');

-- Insert Departments
INSERT INTO `departments` (`id`, `code`, `name`, `description`) VALUES
(1, 'CSE', 'Computer Science & Engineering', 'Department of Computer Science & Engineering'),
(2, 'IT', 'Information Technology', 'Department of Information Technology'),
(3, 'ECE', 'Electronics & Communication Engineering', 'Department of Electronics & Communication Engineering'),
(4, 'EEE', 'Electrical & Electronics Engineering', 'Department of Electrical & Electronics Engineering'),
(5, 'MECH', 'Mechanical Engineering', 'Department of Mechanical Engineering'),
(6, 'CIVIL', 'Civil Engineering', 'Department of Civil Engineering');

-- Insert Achievement Categories
INSERT INTO `achievement_categories` (`id`, `code`, `category_name`, `description`) VALUES
(1, 'PUBLICATION', 'Research Publication', 'Journal articles, conference papers, books, and book chapters'),
(2, 'PATENT', 'Patent / Intellectual Property', 'Filed, published, or granted patents'),
(3, 'RESEARCH_GRANT', 'Research & Consultancy Grant', 'Funded research projects and industrial consultancy'),
(4, 'WORKSHOP_FDP', 'Workshop / FDP / Certification', 'Faculty Development Programs, workshops, and certifications'),
(5, 'AWARD', 'Award & Recognition', 'Honors and awards received from recognized organizations');

-- Seed Initial System Administrator (Password: Admin@123 -> BCrypt hash)
INSERT INTO `users` (`id`, `employee_id`, `full_name`, `email`, `password_hash`, `designation`, `department_id`, `role_id`, `phone`, `status`) VALUES
(1, 'EMP001', 'System Administrator', 'admin@faculty.edu', '$2a$10$7R0wK/Q0Qh3j8G5K3y4w0eXp/G.X5gYq2/eU9q2X3Y4Z5W6V7U8T9', 'Administrator', 1, 1, '9876543210', 'ACTIVE');
