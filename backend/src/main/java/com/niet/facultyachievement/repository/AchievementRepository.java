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

    /* ================================================================
       ACCREDITATION REPORT QUERIES (NAAC / NBA)

       Same literal-not-parameter discipline as the public block above,
       for the same kind of reason. There, a parameter could leak a
       private record to a visitor. Here, it could put an unverified
       claim into a document the institution signs and submits.

           a.status = APPROVED

       is written into the JPQL, so there is no argument any caller can
       pass — today or after some future refactor — that makes these
       methods return a PENDING or REJECTED record.

       Note what is deliberately *not* filtered here: visibility. The
       public site shows only records the author marked PUBLIC, but an
       accreditation report covers the institution's whole verified
       output. A faculty member choosing to keep a paper off the public
       website is not choosing to withhold it from the college's own
       NAAC submission, and treating those as the same setting would
       under-report the institution to its assessors.
       ================================================================ */

    /**
     * Every approved achievement in scope, with all five detail tables loaded.
     *
     * <p><strong>Why all five {@code LEFT JOIN FETCH} in one query.</strong> All
     * five details are {@code @OneToOne} (see {@code Achievement:81-94}), i.e.
     * single-valued, so this produces exactly one row per achievement — no
     * cartesian product, and the {@code LIMIT} is still applied in SQL. (The
     * Hibernate warning about applying pagination in memory is about
     * <em>collection</em> fetches; there are none here.) Without the fetches,
     * building the detail tables would fire five lazy loads per row — the same
     * N+1 that {@link #countPublicAchievementsGroupedByUser()} exists to avoid.
     *
     * <p>No {@code DISTINCT}: with only single-valued joins there is nothing to
     * de-duplicate, and it would only cost a needless SQL-level distinct across
     * every fetched column.
     *
     * <p>Returns {@code List} rather than {@code Page} on purpose — a {@code Page}
     * would make Spring Data derive a count query from a statement carrying five
     * fetch joins. {@link #countApprovedForReport} does that job explicitly and
     * cheaply instead.
     *
     * <p>{@code fromYear} / {@code toYear} compare {@code academicYear} as text.
     * That is a correct chronological range <em>only</em> because the stored
     * format is fixed-width {@code YYYY-YYYY}. It is also why the report derives
     * its year columns from the data rather than trusting this ordering to place
     * every value: {@code AchievementCreateRequest.academicYear} carries no
     * {@code @Pattern}, so an off-format value can exist and would sort wrongly.
     */
    @Query("SELECT a FROM Achievement a "
            + "JOIN FETCH a.user u "
            + "JOIN FETCH u.department d "
            + "JOIN FETCH a.category c "
            + "LEFT JOIN FETCH a.publication "
            + "LEFT JOIN FETCH a.patent "
            + "LEFT JOIN FETCH a.researchGrant "
            + "LEFT JOIN FETCH a.workshopFdp "
            + "LEFT JOIN FETCH a.award "
            + "WHERE a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND (:departmentId IS NULL OR d.id = :departmentId) "
            + "  AND (:fromYear IS NULL OR a.academicYear >= :fromYear) "
            + "  AND (:toYear IS NULL OR a.academicYear <= :toYear) "
            + "ORDER BY d.name ASC, a.academicYear DESC, u.fullName ASC, a.achievementDate DESC")
    List<Achievement> findApprovedForReport(@Param("departmentId") Long departmentId,
                                            @Param("fromYear") String fromYear,
                                            @Param("toYear") String toYear,
                                            Pageable pageable);

    /**
     * How many approved records match the same scope, ignoring the row cap.
     *
     * <p>The report compares this against how many rows it actually loaded. If
     * they differ, it says so on its face. A silently truncated accreditation
     * report is worse than no report at all.
     */
    @Query("SELECT COUNT(a) FROM Achievement a JOIN a.user u JOIN u.department d "
            + "WHERE a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND (:departmentId IS NULL OR d.id = :departmentId) "
            + "  AND (:fromYear IS NULL OR a.academicYear >= :fromYear) "
            + "  AND (:toYear IS NULL OR a.academicYear <= :toYear)")
    long countApprovedForReport(@Param("departmentId") Long departmentId,
                                @Param("fromYear") String fromYear,
                                @Param("toYear") String toYear);

    /**
     * Records in scope that the report had to leave out, as
     * {@code [status, count]} rows.
     *
     * <p>This is what lets the report say "412 of 460 publications, the rest
     * still awaiting departmental verification" instead of just "412". The first
     * sentence is honest about its own completeness; the second quietly is not.
     */
    @Query("SELECT a.status, COUNT(a) FROM Achievement a JOIN a.user u JOIN u.department d "
            + "WHERE a.status <> com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "  AND (:departmentId IS NULL OR d.id = :departmentId) "
            + "  AND (:fromYear IS NULL OR a.academicYear >= :fromYear) "
            + "  AND (:toYear IS NULL OR a.academicYear <= :toYear) "
            + "GROUP BY a.status")
    List<Object[]> countNonApprovedForReport(@Param("departmentId") Long departmentId,
                                             @Param("fromYear") String fromYear,
                                             @Param("toYear") String toYear);
}
