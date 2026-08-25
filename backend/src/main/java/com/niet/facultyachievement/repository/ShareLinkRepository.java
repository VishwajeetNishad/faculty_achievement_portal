package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    /**
     * Find a link by its token — the single hottest query on this table,
     * because every public visit is exactly this lookup.
     *
     * <p>Notice it does <strong>not</strong> filter on {@code revoked} or
     * {@code expiresAt}. That is deliberate. If the query silently skipped
     * dead links, a revoked link and a link that never existed would both
     * come back empty, and the visitor would be told "not found" in both
     * cases. Loading the row and then judging it lets the service answer
     * honestly: 404 for a token that was never issued, 410 REVOKED for one
     * the owner killed, 410 EXPIRED for one that timed out.
     */
    Optional<ShareLink> findByShareToken(String shareToken);

    /**
     * Guards token generation. {@code SecureRandom} collisions are so
     * improbable that this check will realistically never fire, but a
     * one-line existence test is cheaper than the outage that a duplicate
     * would cause, and it lets the generator retry instead of letting the
     * database throw a constraint violation at the user.
     */
    boolean existsByShareToken(String shareToken);

    /**
     * The one live link for an achievement, if there is one.
     *
     * <p>At most one link per achievement is active at a time — creating a
     * new one revokes the old one — so this returns an Optional rather than a
     * list. It only excludes revoked links, not expired ones: an expired link
     * is still the achievement's current link, and the owner needs to see it
     * in order to extend it.
     */
    Optional<ShareLink> findFirstByAchievementIdAndRevokedFalseOrderByCreatedAtDesc(Long achievementId);

    /**
     * Every link this faculty member has ever created, newest first, for the
     * "My Research &amp; Shared Resources" screen.
     *
     * <p>Fetch-joins the achievement and its category in one query. Without
     * that, rendering a list of twenty links would fire forty extra queries
     * to read each title and category name — the classic N+1 problem.
     */
    @Query("SELECT s FROM ShareLink s "
            + "JOIN FETCH s.achievement a "
            + "JOIN FETCH a.category "
            + "WHERE s.createdBy.id = :userId "
            + "ORDER BY s.createdAt DESC")
    List<ShareLink> findAllByCreatorWithAchievement(@Param("userId") Long userId);

    /**
     * The token lookup used by the <em>public</em> pages, with everything the
     * response needs already loaded.
     *
     * <p>Why a second, heavier version of {@link #findByShareToken(String)}:
     * the public share page is the one place in this application with no logged-in
     * user and no {@code @Transactional} service method around it. That is
     * deliberate — see {@code ShareServiceImpl.getSharedAchievement} — and it means
     * the entity is <strong>detached</strong> by the time the response is built.
     * Touching any lazy association at that point throws
     * {@code LazyInitializationException}. So every association the response reads
     * is fetch-joined here, up front, in one query.
     *
     * <p>The five {@code LEFT JOIN FETCH}es are left joins because an achievement
     * has exactly one of those detail rows and four nulls. An inner join on any of
     * them would silently return nothing for every achievement of the other four
     * categories — a link that mysteriously 404s for patents but works for
     * publications.
     *
     * <p>Like {@link #findByShareToken(String)}, it does not filter out dead links:
     * the service has to see a revoked or expired row in order to explain it.
     *
     * <p>{@code createdBy} is fetched even though no public field comes from it,
     * because the {@code SHARE_EXPIRED} audit entry reads the actor's email. An
     * association loaded only on an error path is one that gets missed in testing
     * and then fails in front of a real visitor.
     */
    @Query("SELECT s FROM ShareLink s "
            + "JOIN FETCH s.achievement a "
            + "JOIN FETCH s.createdBy "
            + "JOIN FETCH a.user u "
            + "JOIN FETCH u.department "
            + "JOIN FETCH a.category "
            + "LEFT JOIN FETCH a.publication "
            + "LEFT JOIN FETCH a.patent "
            + "LEFT JOIN FETCH a.researchGrant "
            + "LEFT JOIN FETCH a.workshopFdp "
            + "LEFT JOIN FETCH a.award "
            + "WHERE s.shareToken = :shareToken")
    Optional<ShareLink> findByTokenWithDetails(@Param("shareToken") String shareToken);

    /**
     * Revoke every live link for one achievement in a single statement.
     *
     * <p>Used when a new link replaces the old one, and when a faculty member
     * changes an achievement away from UNLISTED — turning off unlisted
     * sharing must actually stop the sharing, not just change a label while
     * old tokens keep working.
     *
     * <p>{@code @Transactional} is required and easy to forget: Spring Data's
     * class-level read-only transaction covers the methods it generates, but a
     * hand-written {@code @Modifying} query is not automatically given a writable
     * one. Without this annotation the call fails at runtime with "no transaction
     * is in progress" whenever it is made outside a transactional service method.
     */
    @Query("UPDATE ShareLink s SET s.revoked = true, s.revokedAt = :now "
            + "WHERE s.achievement.id = :achievementId AND s.revoked = false")
    @Modifying
    @Transactional
    int revokeAllForAchievement(@Param("achievementId") Long achievementId,
                                @Param("now") LocalDateTime now);

    /**
     * Count one successful open of a link.
     *
     * <p>Written as an UPDATE statement rather than
     * {@code link.setAccessCount(link.getAccessCount() + 1)} because the read and
     * the write have to be one operation. Two people opening the same link at the
     * same moment would both read 4, both write 5, and one visit would vanish.
     * {@code accessCount = accessCount + 1} makes the database do the arithmetic,
     * so both visits land.
     *
     * <p>{@code REQUIRES_NEW} so the counter is committed on its own. Nothing
     * about a page view should be able to roll back, and nothing about a rollback
     * elsewhere should be able to erase it.
     */
    @Query("UPDATE ShareLink s SET s.accessCount = s.accessCount + 1, s.lastAccessedAt = :now "
            + "WHERE s.id = :id")
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordAccess(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Note that somebody has now tried a link that had already expired, and
     * report whether this was the first such attempt.
     *
     * <p>This exists to solve a small but real problem: the audit log should
     * record {@code SHARE_EXPIRED} <em>once</em>, when a dead link is first
     * touched, not once per refresh forever. The guard clause does that entirely
     * in SQL:
     *
     * <ul>
     *   <li>first attempt after expiry — {@code lastAccessedAt} is either null or
     *       from before the expiry moment, so the row matches, {@code lastAccessedAt}
     *       is set to now (which is <em>after</em> {@code expiresAt}), and the method
     *       returns 1 → the caller logs;</li>
     *   <li>every attempt after that — {@code lastAccessedAt} is now later than
     *       {@code expiresAt}, so nothing matches and it returns 0 → the caller
     *       stays quiet.</li>
     * </ul>
     *
     * <p>Because the test and the write are the same statement, two simultaneous
     * visitors cannot both see "first time" and produce two log entries.
     *
     * <p>It deliberately does <strong>not</strong> touch {@code accessCount}. That
     * number is shown to the owner as how many times their link was
     * <em>opened</em>, and a rejected attempt is not an opening.
     *
     * <p>{@code REQUIRES_NEW} matters more here than anywhere else in this file:
     * the caller throws {@code GoneException} immediately after this returns. In a
     * shared transaction the throw would roll the marker back, and the "log only
     * once" logic would log on every single request instead.
     *
     * @return 1 if this was the first post-expiry attempt, 0 otherwise
     */
    @Query("UPDATE ShareLink s SET s.lastAccessedAt = :now "
            + "WHERE s.id = :id "
            + "  AND s.expiresAt IS NOT NULL "
            + "  AND (s.lastAccessedAt IS NULL OR s.lastAccessedAt < s.expiresAt)")
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int markExpiryObserved(@Param("id") Long id, @Param("now") LocalDateTime now);

    long countByCreatedByIdAndRevokedFalse(Long userId);
}
