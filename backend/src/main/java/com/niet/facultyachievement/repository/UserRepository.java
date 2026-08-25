package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmployeeId(String employeeId);
    List<User> findByDepartmentId(Long departmentId);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);

    long countByDepartmentId(Long departmentId);
    long countByStatus(UserStatus status);

    /**
     * How many users hold a given role in a given state.
     *
     * <p>Exists for one reason: the last-administrator guard. Before an account
     * is deactivated or demoted out of the administrator role, the service counts
     * the remaining ACTIVE administrators. If that would reach zero the change is
     * refused with a 409, because nobody would be left who could undo it — the
     * portal would be permanently locked out of its own user management.
     */
    long countByRoleIdAndStatus(Long roleId, UserStatus status);

    /**
     * Excludes one specific user from the count above.
     *
     * <p>Needed because the guard has to ask "how many administrators would still
     * be active if this one were removed?". Counting first and subtracting one by
     * hand would be wrong whenever the user in question is already inactive.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.id = :roleId AND u.status = :status AND u.id <> :excludedUserId")
    long countByRoleAndStatusExcludingUser(@Param("roleId") Long roleId,
                                           @Param("status") UserStatus status,
                                           @Param("excludedUserId") Long excludedUserId);

    /**
     * Users per department as {@code [departmentId, count]} rows.
     *
     * <p>One GROUP BY query instead of a count per department, matching the
     * aggregation style already used in {@code DashboardServiceImpl}. With a
     * handful of departments the difference is small; the point is that the
     * management screen does not get slower as departments are added.
     */
    @Query("SELECT u.department.id, COUNT(u) FROM User u WHERE u.department IS NOT NULL GROUP BY u.department.id")
    List<Object[]> countUsersGroupedByDepartment();

    /* ================================================================
       PUBLIC SLUGS (Track B)
       ================================================================ */

    /**
     * Slug lookup for {@code PublicSlugBackfill}'s collision handling and for
     * {@code UserManagementService} when it assigns a slug to a new account.
     */
    boolean existsByPublicSlug(String publicSlug);

    /**
     * Everyone still missing a public slug, for the startup backfill.
     *
     * <p>The department is fetch-joined because the slug is built from its code, and
     * the backfill runs in a {@code CommandLineRunner} — there is no HTTP request, so
     * {@code spring.jpa.open-in-view} does not apply and the entity is detached the
     * moment this returns. Without the join, reading
     * {@code user.getDepartment().getCode()} throws
     * {@code LazyInitializationException} and the application refuses to start.
     *
     * <p>{@code LEFT} rather than an inner join so a user with no department (which
     * the schema forbids, but the generator still handles) is not silently skipped
     * and left without an address forever.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.publicSlug IS NULL")
    List<User> findByPublicSlugIsNull();

    /**
     * Find a person by their public address, for their public profile page.
     *
     * <p>Three conditions beyond the slug itself, all embedded in the query
     * rather than left to the caller:
     *
     * <ul>
     *   <li><strong>ACTIVE</strong> — a deactivated person's profile must stop
     *       resolving, or deactivating somebody would leave their name and
     *       research on a public page indefinitely;</li>
     *   <li><strong>not an administrator</strong> — an admin account is an
     *       operations login, not an academic identity;</li>
     *   <li><strong>owns at least one publicly visible achievement</strong> —
     *       this is the condition that matters most, and it is worth being
     *       explicit about why. Slugs are built from real names, so they are
     *       guessable. If any active account's profile resolved, anyone could
     *       walk a list of common names and harvest the staff roster complete
     *       with designations and departments. Requiring public research means
     *       a profile only exists once its owner has chosen to publish
     *       something, and the profile page and the directory then agree with
     *       each other about who is on the public site.</li>
     * </ul>
     *
     * <p>The consequence — a faculty member with nothing public gets "profile
     * not found" — is the correct answer to the question being asked. They do
     * not have a public profile.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.department "
            + "WHERE u.publicSlug = :slug "
            + "  AND u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE "
            + "  AND u.role.name <> 'ROLE_ADMIN' "
            + "  AND EXISTS (SELECT 1 FROM Achievement a "
            + "              WHERE a.user = u "
            + "                AND a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "                AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC) ")
    Optional<User> findPublicProfileBySlug(@Param("slug") String slug);

    /**
     * The public faculty directory.
     *
     * <p>Restricted to ACTIVE accounts that actually have a slug, and to people
     * who own at least one publicly visible achievement. That last condition is
     * a judgement call worth stating: a directory listing every account would
     * expose the full staff roster, including administrative logins, to anyone
     * on the internet. Listing only those with public research keeps the page
     * to what it claims to be — a research directory, not a staff list.
     *
     * <p>Administrators are excluded outright. An admin account is an operations
     * login, not an academic identity, and it has no business on a public page
     * even if somebody attached an achievement to it.
     */
    @Query("SELECT DISTINCT u FROM User u "
            + "JOIN u.department d "
            + "WHERE u.status = com.niet.facultyachievement.entity.UserStatus.ACTIVE "
            + "  AND u.publicSlug IS NOT NULL "
            + "  AND u.role.name <> 'ROLE_ADMIN' "
            + "  AND (:departmentCode IS NULL OR d.code = :departmentCode) "
            + "  AND (:keyword IS NULL "
            + "       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "       OR LOWER(u.designation) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "       OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "  AND EXISTS (SELECT 1 FROM Achievement a "
            + "              WHERE a.user = u "
            + "                AND a.status = com.niet.facultyachievement.entity.AchievementStatus.APPROVED "
            + "                AND a.visibility = com.niet.facultyachievement.entity.AchievementVisibility.PUBLIC) ")
    Page<User> searchPublicFaculty(@Param("keyword") String keyword,
                                   @Param("departmentCode") String departmentCode,
                                   Pageable pageable);
}
