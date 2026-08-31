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

    // An administrator set a new password for someone who had lost theirs.
    // Recorded as its own action so it stands out in the trail — the entry notes
    // only that a reset happened, never the password itself.
    PASSWORD_RESET,

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
    SHARE_EXPIRED,

    // Home page highlight banners. Worth auditing even though the content is
    // only marketing: these images are the first thing a visitor sees, so
    // "who put that on the front page, and when" is a question somebody will
    // eventually ask.
    HIGHLIGHT_CREATED,
    HIGHLIGHT_UPDATED,
    HIGHLIGHT_DELETED,

    // Someone downloaded the institution-wide accreditation report. Only the
    // export is audited, not every time the page is viewed: reading a report on
    // screen leaves the data inside the application, while downloading it puts
    // every faculty member's verified record into a file that travels. The entry
    // records the filters used, so the trail says which slice left the building.
    REPORT_EXPORTED
}
