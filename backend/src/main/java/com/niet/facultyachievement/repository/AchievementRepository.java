package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO;
import com.niet.facultyachievement.dto.dashboard.CategoryStatDTO;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long>,
        JpaSpecificationExecutor<Achievement> {
    List<Achievement> findByUserId(Long userId);
    List<Achievement> findByUserIdAndStatus(Long userId, AchievementStatus status);
    List<Achievement> findByCategoryId(Long categoryId);
    List<Achievement> findByStatus(AchievementStatus status);
    List<Achievement> findByAcademicYear(String academicYear);
    List<Achievement> findByUserDepartmentId(Long departmentId);

    // --- Aggregate Counts ---
    long countByUserId(Long userId);
    long countByUserIdAndStatus(Long userId, AchievementStatus status);

    long countByUserDepartmentId(Long departmentId);
    long countByUserDepartmentIdAndStatus(Long departmentId, AchievementStatus status);

    long countByStatus(AchievementStatus status);

    // --- Top 5 Recent Achievements ---
    List<Achievement> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Achievement> findTop5ByUserDepartmentIdOrderByCreatedAtDesc(Long departmentId);

    // --- Category Analytics Aggregations ---
    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.CategoryStatDTO(a.category.categoryName, COUNT(a)) FROM Achievement a WHERE a.user.id = :userId GROUP BY a.category.categoryName")
    List<CategoryStatDTO> getCategoryStatsByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.CategoryStatDTO(a.category.categoryName, COUNT(a)) FROM Achievement a WHERE a.user.department.id = :departmentId GROUP BY a.category.categoryName")
    List<CategoryStatDTO> getCategoryStatsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.CategoryStatDTO(a.category.categoryName, COUNT(a)) FROM Achievement a GROUP BY a.category.categoryName")
    List<CategoryStatDTO> getOverallCategoryStats();

    // --- Academic Year Analytics Aggregations ---
    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO(a.academicYear, COUNT(a)) FROM Achievement a WHERE a.user.id = :userId GROUP BY a.academicYear")
    List<AcademicYearStatDTO> getAcademicYearStatsByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO(a.academicYear, COUNT(a)) FROM Achievement a WHERE a.user.department.id = :departmentId GROUP BY a.academicYear")
    List<AcademicYearStatDTO> getAcademicYearStatsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT new com.niet.facultyachievement.dto.dashboard.AcademicYearStatDTO(a.academicYear, COUNT(a)) FROM Achievement a GROUP BY a.academicYear")
    List<AcademicYearStatDTO> getOverallAcademicYearStats();

    /* ================================================================
       PUBLIC QUERIES (Track B)

       Everything below serves anonymous visitors, and every one of them
       hard-codes the same rule directly into the JPQL:

           a.status = APPROVED  AND  a.visibility = PUBLIC
           AND a.user.status = ACTIVE

       The rule is written as a literal, not passed in as a parameter,
       and that is the point. A parameter can be null, can be bound from
       the wrong variable, or can be plumbed through from a request by a
       future change that looked harmless. A literal cannot: there is no
       argument you can pass to any of these methods that would make them
       return a pending, rejected, private or unlisted record. The methods
       are named "...Public..." so a caller cannot reach for them by
       accident either.

       The user must also be ACTIVE. A deactivated account's work
       disappears from the public site — otherwise deactivating somebody
       would leave their name and research on a public page indefinitely.
       ================================================================ */

    /**
     * The public gallery: every publicly visible achievement, newest first,
     * with optional keyword, category and department filters.
     *
     * <p>The three filters are all "null means no filter", which keeps one
     * query serving the unfiltered gallery, a category tab, a department
     * filter and a search box rather than four near-identical queries.
     *
     * <p>Keyword matching covers title, keywords and description. Description
     * is included because an abstract is often where the subject terms actually
     * are, and a visitor searching "federated learning" expects to find a paper
     * whose title says "distributed model training".
     */
    @Query("SELECT DISTINCT a FROM Achievement a "
            + "JOIN a.user u "
            + "JOIN u.department d "
            + "JOIN a.category c "
            + "LEFT JOIN a.publication pub "
            + "WHERE a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC "
            + "  AND u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE "
            + "  AND (:categoryCode IS NULL OR c.code = :categoryCode) "
            + "  AND (:departmentCode IS NULL OR d.code = :departmentCode) "
            + "  AND (:keyword IS NULL "
            + "       OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "       OR LOWER(a.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "       OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "       OR LOWER(pub.journalConferenceName) LIKE LOWER(CONCAT('%', :keyword, '%'))) ")
    Page<Achievement> searchPublicAchievements(@Param("keyword") String keyword,
                                              @Param("categoryCode") String categoryCode,
                                              @Param("departmentCode") String departmentCode,
                                              Pageable pageable);

    /**
     * One faculty member's publicly visible achievements, for their profile page.
     *
     * <p>Looked up by public slug rather than by id, so the endpoint never needs
     * to accept or reveal an internal user id.
     */
    @Query("SELECT a FROM Achievement a "
            + "JOIN a.user u "
            + "JOIN a.category c "
            + "WHERE u.publicSlug = :slug "
            + "  AND a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC "
            + "  AND u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE "
            + "  AND (:categoryCode IS NULL OR c.code = :categoryCode) ")
    Page<Achievement> findPublicAchievementsBySlug(@Param("slug") String slug,
                                                   @Param("categoryCode") String categoryCode,
                                                   Pageable pageable);

    /**
     * Directory counts as {@code [userId, totalPublic, publicPublications]} rows.
     *
     * <p>One grouped query for the whole directory instead of two counts per
     * person. With a hundred faculty the per-person version would fire two
     * hundred queries to draw one page — the N+1 problem the dashboard code
     * already avoids the same way.
     *
     * <p>Both numbers come from the same filtered set as the achievement list
     * itself, which is what stops a card reading "12 achievements" above a
     * profile page that lists two. A count that disagrees with its own list is
     * a disclosure: it tells the visitor how much they are not being shown.
     */
    @Query("SELECT u.id, COUNT(a), "
            + "       SUM(CASE WHEN c.code = 'PUBLICATION' THEN 1 ELSE 0 END) "
            + "FROM Achievement a "
            + "JOIN a.user u "
            + "JOIN a.category c "
            + "WHERE a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC "
            + "  AND u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE "
            + "GROUP BY u.id")
    List<Object[]> countPublicAchievementsGroupedByUser();

    /**
     * Per-category public counts for one person as {@code [categoryCode, count]}
     * rows. Drives the "areas of work" chips and the category tabs on a public
     * profile.
     */
    @Query("SELECT c.code, COUNT(a) FROM Achievement a "
            + "JOIN a.user u "
            + "JOIN a.category c "
            + "WHERE u.publicSlug = :slug "
            + "  AND a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC "
            + "  AND u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE "
            + "GROUP BY c.code")
    List<Object[]> countPublicAchievementsByCategoryForSlug(@Param("slug") String slug);

    /** How many achievements are publicly visible in total, for the home page. */
    @Query("SELECT COUNT(a) FROM Achievement a JOIN a.user u "
            + "WHERE a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC "
            + "  AND u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE")
    long countPubliclyVisible();
}
