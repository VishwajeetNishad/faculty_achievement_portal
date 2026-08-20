package com.niet.facultyachievement.specification;

import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data JPA Specification builder for Achievement entity.
 *
 * Builds a dynamic Predicate list based on the supplied search criteria.
 * The caller (service layer) establishes the mandatory scope predicates first
 * (userId for FACULTY, departmentId for HOD, nothing extra for ADMIN),
 * then the user-supplied filters are AND-ed on top.
 *
 * This keeps authorization separate from and always applied before filtering.
 */
public class AchievementSpecification {

    // Whitelist of columns the client is permitted to sort on
    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "achievementDate", "createdAt", "title", "academicYear", "status"
    );

    private AchievementSpecification() {}

    /**
     * Returns true when the supplied sortBy field is safe (whitelisted).
     */
    public static boolean isSortFieldAllowed(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) return true;
        return ALLOWED_SORT_FIELDS.contains(sortBy.trim());
    }

    /**
     * Build a Specification that scopes results to a specific user (FACULTY scope).
     */
    public static Specification<Achievement> forUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    /**
     * Build a Specification that scopes results to a specific department (HOD scope).
     */
    public static Specification<Achievement> forDepartment(Long departmentId) {
        return (root, query, cb) ->
                cb.equal(root.get("user").get("department").get("id"), departmentId);
    }

    /**
     * Build filter predicates from optional search criteria.
     * These are AND-combined on top of the mandatory scope specification.
     */
    public static Specification<Achievement> withFilters(
            String keyword,
            AchievementStatus status,
            Long categoryId,
            String categoryCode,
            String academicYear,
            LocalDate fromDate,
            LocalDate toDate,
            Long filterDepartmentId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Keyword: search title OR description (case-insensitive LIKE)
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase().trim() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descMatch  = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }

            // Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Category filter by ID (preferred)
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            } else if (categoryCode != null && !categoryCode.isBlank()) {
                // fall-back to code if no ID provided
                predicates.add(cb.equal(
                        cb.lower(root.get("category").get("code")),
                        categoryCode.toLowerCase().trim()
                ));
            }

            // Academic year filter (exact match)
            if (academicYear != null && !academicYear.isBlank()) {
                predicates.add(cb.equal(root.get("academicYear"), academicYear.trim()));
            }

            // Department filter (ADMIN only — additional filter on top of institution-wide scope)
            if (filterDepartmentId != null) {
                predicates.add(cb.equal(root.get("user").get("department").get("id"), filterDepartmentId));
            }

            // Date range filter on achievementDate
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("achievementDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("achievementDate"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
