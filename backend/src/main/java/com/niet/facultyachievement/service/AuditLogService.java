package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AuditLogResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.User;

import java.time.LocalDate;

public interface AuditLogService {

    void logAction(AuditAction action, String entityType, Long entityId, String description, User actor, String ipAddress);

    void logAction(AuditAction action, String entityType, Long entityId, String description, String actorEmail, String ipAddress);

    PagedResponse<AuditLogResponse> searchAuditLogs(
            AuditAction action,
            String entityType,
            Long actorUserId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir
    );
}
