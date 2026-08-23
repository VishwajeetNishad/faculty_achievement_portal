package com.niet.facultyachievement.entity;

/**
 * Every kind of event recorded in the audit trail.
 *
 * <p>Adding a value here needs NO database migration: {@code audit_logs.action}
 * is a {@code VARCHAR(50)} column (see V1__initial_schema.sql), not a MySQL
 * ENUM, so new names are stored as-is.
 *
 * <p>Audit descriptions must never contain a password, password hash, JWT,
 * share token or any other secret.
 */
public enum AuditAction {
    // Authentication
    LOGIN_SUCCESS,
    LOGIN_FAILURE,

    // Achievements
    ACHIEVEMENT_CREATED,
    ACHIEVEMENT_UPDATED,
    ACHIEVEMENT_DELETED,
    ACHIEVEMENT_APPROVED,
    ACHIEVEMENT_REJECTED,
    PROOF_UPLOADED,
    PROOF_DELETED,

    // Profile / user management
    PROFILE_UPDATED,
    USER_CREATED,
    USER_UPDATED,
    USER_STATUS_CHANGED,
    ROLE_CHANGED,

    // Permission management
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    PERMISSIONS_UPDATED,

    // Department management
    DEPARTMENT_CREATED,
    DEPARTMENT_UPDATED,
    DEPARTMENT_DELETED,

    // Research sharing (unlisted share links)
    SHARE_CREATED,
    SHARE_UPDATED,
    SHARE_REVOKED,
    SHARE_EXPIRED
}
