package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.ShareLinkCreateRequest;
import com.niet.facultyachievement.dto.ShareLinkResponse;
import com.niet.facultyachievement.dto.publicview.SharedAchievementResponse;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Temporary, no-login links to a single achievement.
 *
 * <p>The interface splits cleanly in two, and the split is the design:
 *
 * <ul>
 *   <li><strong>Owner operations</strong> — create, read, extend, revoke, list.
 *       Every one takes an {@code ownerUserId} that the controller supplies from
 *       the security context. None of them takes it from a request body.</li>
 *   <li><strong>Token operations</strong> — resolve a token, stream a document.
 *       These have no user at all, because the caller is anonymous. The token is
 *       the entire credential.</li>
 * </ul>
 *
 * <p>No method lets a caller act on a link they do not own, and no method lets
 * an owner name themselves. Those two properties are what stop this feature from
 * becoming a way to publish somebody else's work.
 */
public interface ShareService {

    /* ── Owner operations ─────────────────────────────────────────────── */

    /**
     * Create a link for one of your own achievements, replacing any existing one.
     *
     * @param ownerUserId from the security context, never from the request
     * @throws org.springframework.security.access.AccessDeniedException if the
     *         caller does not own the achievement
     */
    ShareLinkResponse createShareLink(Long achievementId, Long ownerUserId, ShareLinkCreateRequest request);

    /**
     * The current link for one of your achievements, or {@code null} if there
     * isn't one.
     *
     * <p>Null rather than a 404, because "this achievement has no share link" is
     * a normal state the sharing panel needs to render, not an error.
     */
    ShareLinkResponse getShareLink(Long achievementId, Long ownerUserId);

    /** Change the expiry or the proof-document setting on your existing link. */
    ShareLinkResponse updateShareLink(Long achievementId, Long ownerUserId, ShareLinkCreateRequest request);

    /** Kill your link immediately. Idempotent — revoking twice is not an error. */
    void revokeShareLink(Long achievementId, Long ownerUserId);

    /** Every link you have created, newest first. */
    List<ShareLinkResponse> getMyShareLinks(Long ownerUserId);

    /* ── Token operations (anonymous callers) ─────────────────────────── */

    /**
     * Resolve a token to the achievement behind it.
     *
     * @throws com.niet.facultyachievement.exception.ResourceNotFoundException
     *         if no such token was ever issued
     * @throws com.niet.facultyachievement.exception.GoneException
     *         if the link has expired ({@code EXPIRED}) or been revoked
     *         ({@code REVOKED})
     */
    SharedAchievementResponse getSharedAchievement(String token);

    /**
     * Stream the proof PDF behind a token.
     *
     * <p>Re-checks the token, the expiry and the revocation from scratch. It does
     * not trust the fact that {@link #getSharedAchievement(String)} succeeded a
     * moment ago — a link can expire between the page loading and the visitor
     * clicking download, and this is the request that must notice.
     *
     * <p>There is deliberately no companion "what is the filename" method. The
     * stored name is a UUID that means nothing to a visitor, and the controller
     * offers a fixed, generic filename instead — one less internal detail on its
     * way out of the building.
     *
     * @throws org.springframework.security.access.AccessDeniedException if the
     *         owner did not opt in to sharing the document
     */
    Resource getSharedProofDocument(String token);
}
