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

    /* ================================================================
       LAST-LINE-OF-DEFENCE REDACTION

       Callers are already written never to put a secret in a description.
       This is the safety net for the day somebody forgets, because an audit
       row is permanent: once a password or a share token is written here it
       is in the backup tapes too.

       Two kinds of rule, because a secret can arrive two ways.
       ================================================================ */

    /** A secret written as a labelled pair, e.g. {@code password=hunter2} or {@code token: abc}. */
    private static final java.util.regex.Pattern LABELLED_SECRET = java.util.regex.Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|secret|apikey|api_key)\\s*[=:]\\s*\\S+");

    /** A BCrypt hash, recognisable by its own format whatever text surrounds it. */
    private static final java.util.regex.Pattern BCRYPT_HASH = java.util.regex.Pattern.compile(
            "\\$2[aby]\\$\\d\\d\\$[./A-Za-z0-9]{20,}");

    /** A JWT: three dot-separated Base64 chunks, the first starting with the tell-tale {@code eyJ}. */
    private static final java.util.regex.Pattern JWT = java.util.regex.Pattern.compile(
            "eyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}");

    /**
     * A long unbroken run of URL-safe Base64 — the shape of a share-link token,
     * which is 43 characters. Real prose does not contain 40-character words, and
     * a DOI, a file path or a URL all contain characters that break the run.
     */
    private static final java.util.regex.Pattern LONG_OPAQUE_TOKEN = java.util.regex.Pattern.compile(
            "\\b[A-Za-z0-9_-]{40,}\\b");

    /**
     * Strip anything that looks like a credential, then cap the length.
     *
     * <p>Note what this deliberately does <em>not</em> do: it does not redact on the
     * mere presence of the word "password". An earlier version matched
     * {@code password} followed by whitespace and swallowed the next word, which
     * turned the honest description "Reset the password for a.b@niet.ac.in" into
     * "password=[REDACTED] a.b@niet.ac.in" — unreadable, and it hid the one detail
     * that made the row useful. Requiring an {@code =} or {@code :} separator keeps
     * ordinary English intact, and the three format-based rules below more than make
     * up for it: they catch a hash, a JWT or a token even when no label is nearby,
     * which the old rule could never do.
     */
    private String sanitizeDescription(String desc) {
        if (desc == null) return null;

        String clean = desc;
        clean = LABELLED_SECRET.matcher(clean).replaceAll("$1=[REDACTED]");
        clean = BCRYPT_HASH.matcher(clean).replaceAll("[REDACTED_HASH]");
        clean = JWT.matcher(clean).replaceAll("[REDACTED_JWT]");
        clean = LONG_OPAQUE_TOKEN.matcher(clean).replaceAll("[REDACTED_TOKEN]");

        if (clean.length() > 500) {
            clean = clean.substring(0, 497) + "...";
        }
        return clean;
    }
}
