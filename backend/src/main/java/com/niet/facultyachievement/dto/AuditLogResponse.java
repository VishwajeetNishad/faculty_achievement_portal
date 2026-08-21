package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.AuditLog;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long id;
    private Long actorUserId;
    private String actorName;
    private String actorEmail;
    private AuditAction action;
    private String entityType;
    private Long entityId;
    private String description;
    private String ipAddress;
    private LocalDateTime createdAt;

    public static AuditLogResponse fromEntity(AuditLog log) {
        if (log == null) return null;
        var actor = log.getActor();

        return AuditLogResponse.builder()
                .id(log.getId())
                .actorUserId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : (log.getActorEmail() != null ? log.getActorEmail() : "System / Guest"))
                .actorEmail(actor != null ? actor.getEmail() : log.getActorEmail())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
