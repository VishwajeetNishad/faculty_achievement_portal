package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AuditLogResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.AuditLog;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdat", "id", "action", "entitytype"
    );

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(AuditAction action, String entityType, Long entityId, String description, User actor, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .actor(actor)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .action(action)
                .entityType(entityType != null ? entityType.toUpperCase().trim() : "SYSTEM")
                .entityId(entityId)
                .description(sanitizeDescription(description))
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(AuditAction action, String entityType, Long entityId, String description, String actorEmail, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .actor(null)
                .actorEmail(actorEmail)
                .action(action)
                .entityType(entityType != null ? entityType.toUpperCase().trim() : "SYSTEM")
                .entityId(entityId)
                .description(sanitizeDescription(description))
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> searchAuditLogs(
            AuditAction action,
            String entityType,
            Long actorUserId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 15;
        if (size > 100) size = 100;

        String rawSort = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(rawSort.toLowerCase())) {
            throw new BadRequestException("Sort field '" + sortBy + "' is not allowed for audit log query.");
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, rawSort));

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.toUpperCase().trim()));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), actorUserId));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate.atTime(23, 59, 59)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> resultPage = auditLogRepository.findAll(spec, pageable);
        return PagedResponse.from(resultPage, AuditLogResponse::fromEntity);
    }

    private String sanitizeDescription(String desc) {
        if (desc == null) return null;
        // Never allow passwords, hashes, tokens or raw secrets to be persisted in description
        String clean = desc;
        clean = clean.replaceAll("(?i)password[\\s=:]+\\S+", "password=[REDACTED]");
        clean = clean.replaceAll("(?i)token[\\s=:]+\\S+", "token=[REDACTED]");
        if (clean.length() > 500) {
            clean = clean.substring(0, 497) + "...";
        }
        return clean;
    }
}
