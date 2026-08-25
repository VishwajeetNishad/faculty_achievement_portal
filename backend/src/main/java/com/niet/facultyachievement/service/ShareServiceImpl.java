package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.ShareDuration;
import com.niet.facultyachievement.dto.ShareLinkCreateRequest;
import com.niet.facultyachievement.dto.ShareLinkResponse;
import com.niet.facultyachievement.dto.publicview.SharedAchievementResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.ShareLink;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.GoneException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.ShareLinkRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Share links: creating them, policing them, and resolving them for strangers.
 *
 * <p>Three rules hold everywhere in this class, and everything else is detail:
 *
 * <ol>
 *   <li><strong>The owner is never named by the caller.</strong> Every owner
 *       method takes an {@code ownerUserId} that the controller read out of the
 *       security context. Nothing here reads a user id from a request body.</li>
 *   <li><strong>The token is the whole credential.</strong> It is 32 bytes of
 *       {@link SecureRandom}, so it cannot be guessed, and it is checked from
 *       scratch on every single anonymous request — never trusted because it
 *       worked a moment ago.</li>
 *   <li><strong>Expiry and revocation are decided here, on the server.</strong>
 *       The countdown the visitor sees in their browser is decoration.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkRepository shareLinkRepository;
    private final AchievementRepository achievementRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    /**
     * Where share links point. Configured, not derived from the incoming request,
     * so the address an owner copies is the real public address of the portal and
     * not whatever host header happened to arrive.
     */
    @Value("${app.share.base-url:http://localhost:5500}")
    private String shareBaseUrl;

    /**
     * One generator for the whole application.
     *
     * <p>{@code SecureRandom} is thread-safe, and creating a fresh one per call is
     * both slower and, on some platforms, a re-seeding hazard. One shared instance
     * is the standard way to use it.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 32 bytes = 256 bits of entropy, the same order as a modern session key.
     * Base64-url without padding turns that into 43 characters.
     */
    private static final int TOKEN_BYTES = 32;

    /** How many times to retry if a generated token somehow already exists. */
    private static final int TOKEN_ATTEMPTS = 5;

    /* ── Owner operations ─────────────────────────────────────────────── */

    @Override
    @Transactional
    public ShareLinkResponse createShareLink(Long achievementId, Long ownerUserId, ShareLinkCreateRequest request) {
        Achievement achievement = loadOwnedAchievement(achievementId, ownerUserId);
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + ownerUserId));

        LocalDateTime expiresAt = resolveExpiry(request);

        // Only one live link per achievement. Creating a second one without
        // killing the first would quietly double the number of credentials
        // pointing at this record, and the owner would have no way to see it.
        int replaced = shareLinkRepository.revokeAllForAchievement(achievementId, LocalDateTime.now());

        ShareLink link = ShareLink.builder()
                .achievement(achievement)
                .createdBy(owner)
                .shareToken(generateUniqueToken())
                .expiresAt(expiresAt)
                .includeProofDocument(request.isIncludeProofDocument())
                .revoked(false)
                .accessCount(0L)
                .build();

        ShareLink saved = shareLinkRepository.save(link);

        // The description records what was decided, never the token itself.
        auditLogService.logAction(
                AuditAction.SHARE_CREATED,
                "SHARE_LINK",
                saved.getId(),
                "Created share link for achievement id " + achievementId
                        + " (expires: " + describeExpiry(expiresAt)
                        + ", proof document: " + (saved.isIncludeProofDocument() ? "included" : "excluded")
                        + (replaced > 0 ? ", replacing " + replaced + " previous link(s)" : "")
                        + ")",
                owner,
                null
        );

        return ShareLinkResponse.fromEntity(saved, buildShareUrl(saved.getShareToken()));
    }

    @Override
    @Transactional(readOnly = true)
    public ShareLinkResponse getShareLink(Long achievementId, Long ownerUserId) {
        loadOwnedAchievement(achievementId, ownerUserId);

        // Null, not a 404. "This achievement has no share link yet" is the normal
        // state of most achievements, and the sharing panel has to render it.
        return shareLinkRepository
                .findFirstByAchievementIdAndRevokedFalseOrderByCreatedAtDesc(achievementId)
                .map(link -> ShareLinkResponse.fromEntity(link, buildShareUrl(link.getShareToken())))
                .orElse(null);
    }

    @Override
    @Transactional
    public ShareLinkResponse updateShareLink(Long achievementId, Long ownerUserId, ShareLinkCreateRequest request) {
        loadOwnedAchievement(achievementId, ownerUserId);

        ShareLink link = shareLinkRepository
                .findFirstByAchievementIdAndRevokedFalseOrderByCreatedAtDesc(achievementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No share link exists for achievement id: " + achievementId));

        LocalDateTime expiresAt = resolveExpiry(request);

        // An expired link can be brought back to life by extending it. That is the
        // point of "Extend" in the UI, and it is safe: the token never changed
        // hands, and the owner is deliberately re-opening their own link.
        link.setExpiresAt(expiresAt);
        link.setIncludeProofDocument(request.isIncludeProofDocument());

        ShareLink saved = shareLinkRepository.save(link);

        auditLogService.logAction(
                AuditAction.SHARE_UPDATED,
                "SHARE_LINK",
                saved.getId(),
                "Updated share link for achievement id " + achievementId
                        + " (expires: " + describeExpiry(expiresAt)
                        + ", proof document: " + (saved.isIncludeProofDocument() ? "included" : "excluded") + ")",
                saved.getCreatedBy(),
                null
        );

        return ShareLinkResponse.fromEntity(saved, buildShareUrl(saved.getShareToken()));
    }

    @Override
    @Transactional
    public void revokeShareLink(Long achievementId, Long ownerUserId) {
        Achievement achievement = loadOwnedAchievement(achievementId, ownerUserId);

        int revoked = shareLinkRepository.revokeAllForAchievement(achievementId, LocalDateTime.now());

        // Idempotent on purpose. Revoking a link that is already dead is not an
        // error — it is somebody clicking twice, or a retry after a dropped
        // response, and the outcome they wanted is already true.
        if (revoked > 0) {
            auditLogService.logAction(
                    AuditAction.SHARE_REVOKED,
                    "SHARE_LINK",
                    achievementId,
                    "Revoked " + revoked + " share link(s) for achievement id " + achievementId,
                    achievement.getUser(),
                    null
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShareLinkResponse> getMyShareLinks(Long ownerUserId) {
        return shareLinkRepository.findAllByCreatorWithAchievement(ownerUserId).stream()
                .map(link -> ShareLinkResponse.fromEntity(link, buildShareUrl(link.getShareToken())))
                .collect(Collectors.toList());
    }

    /* ── Token operations (anonymous callers) ─────────────────────────── */

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately <strong>not</strong> {@code @Transactional}. Two reasons,
     * and both matter:
     *
     * <ul>
     *   <li>the lookup fetch-joins everything the response needs, so there is no
     *       lazy loading left to protect and no reason to hold a connection open
     *       while a DTO is assembled;</li>
     *   <li>this method throws {@link GoneException} on a dead link. Inside a
     *       transaction that throw would roll back the "expiry observed" marker
     *       written a line earlier, and the audit log would then record
     *       {@code SHARE_EXPIRED} on every refresh instead of once. The two small
     *       writes below commit in their own transactions
     *       ({@code REQUIRES_NEW} on the repository methods) precisely so that
     *       they survive the exception.</li>
     * </ul>
     */
    @Override
    public SharedAchievementResponse getSharedAchievement(String token) {
        ShareLink link = resolveLiveLink(token);

        // Count the visit. Not inside the response mapping, because a failed
        // render should not be recorded as a successful open.
        shareLinkRepository.recordAccess(link.getId(), LocalDateTime.now());

        return SharedAchievementResponse.fromEntity(link, link.getAchievement());
    }

    @Override
    public Resource getSharedProofDocument(String token) {
        ShareLink link = resolveLiveLink(token);
        Achievement achievement = link.getAchievement();

        // Sharing the record is not sharing the file. The owner has to have said
        // yes to this separately, and this is where that answer is enforced.
        if (!link.isIncludeProofDocument()) {
            throw new AccessDeniedException("This share link does not include the proof document");
        }

        String filename = proofFilename(achievement);
        return fileStorageService.loadFileAsResource(filename);
    }

    /* ── Internals ────────────────────────────────────────────────────── */

    /**
     * Load an achievement and prove the caller owns it, or refuse.
     *
     * <p>This is the same shape as the ownership check used throughout
     * {@code AchievementServiceImpl}, and it is repeated rather than shared on
     * purpose: an authorisation check that lives next to the code it protects is
     * one that a future reader cannot miss.
     *
     * <p>404 before 403 is intentional — the record either exists or it does not,
     * and that fact is not a secret. The interesting refusal is the second one.
     */
    private Achievement loadOwnedAchievement(Long achievementId, Long ownerUserId) {
        Achievement achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Achievement not found with id: " + achievementId));

        if (!achievement.getUser().getId().equals(ownerUserId)) {
            throw new AccessDeniedException(
                    "You can only manage share links for your own achievements");
        }
        return achievement;
    }

    /**
     * Turn a token into a usable link, or explain why it is not usable.
     *
     * <p>The three outcomes are all different on purpose:
     *
     * <ul>
     *   <li>no such token — 404. It was never valid; check for a typo.</li>
     *   <li>revoked — 410 {@code REVOKED}. The owner withdrew it.</li>
     *   <li>expired — 410 {@code EXPIRED}. Ask the sender for a fresh one.</li>
     * </ul>
     *
     * <p>Every anonymous request runs this from the beginning. A link that was
     * alive when the page loaded may be dead by the time the visitor clicks
     * "download", and this is the code that notices.
     */
    private ShareLink resolveLiveLink(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Share link not found");
        }

        ShareLink link = shareLinkRepository.findByTokenWithDetails(token)
                // The message never repeats the token back. Echoing a secret into
                // an error body is how secrets end up in log files and browser
                // history.
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        if (link.isRevoked()) {
            throw new GoneException("REVOKED", "This share link has been revoked by its owner.");
        }

        if (link.isExpired()) {
            // Log once, on the first attempt after expiry — the UPDATE's WHERE
            // clause is what makes it once. See ShareLinkRepository#markExpiryObserved.
            int firstObservation = shareLinkRepository.markExpiryObserved(link.getId(), LocalDateTime.now());
            if (firstObservation > 0) {
                auditLogService.logAction(
                        AuditAction.SHARE_EXPIRED,
                        "SHARE_LINK",
                        link.getId(),
                        "Share link for achievement id " + link.getAchievement().getId()
                                + " was accessed after expiring at " + link.getExpiresAt(),
                        link.getCreatedBy(),
                        null
                );
            }
            throw new GoneException("EXPIRED", "This share link has expired.");
        }

        return link;
    }

    /**
     * The stored filename behind an achievement's proof document.
     *
     * <p>Reuses the existing storage layer completely: the physical directory is
     * never exposed, the filename is the UUID name that
     * {@code FileStorageServiceImpl} generated at upload time, and
     * {@code loadFileAsResource} still applies its own path-traversal guard. The
     * only thing this method adds is a token-based reason to be allowed in, in
     * place of the user-based one.
     */
    private String proofFilename(Achievement achievement) {
        String proofUrl = achievement.getProofDocumentUrl();
        if (proofUrl == null || proofUrl.isBlank()) {
            throw new ResourceNotFoundException("No proof document is attached to this achievement");
        }

        String filename = extractFilenameFromUrl(proofUrl);
        if (filename == null || filename.isBlank()) {
            throw new ResourceNotFoundException("Invalid proof document reference");
        }
        return filename;
    }

    /**
     * Pull the stored filename out of the API-relative proof URL.
     *
     * <p>Copied from {@code AchievementServiceImpl} rather than shared, so that
     * the authenticated download path stays untouched by this feature. Track B is
     * additive; it does not get to edit the code that guards logged-in access.
     */
    private String extractFilenameFromUrl(String url) {
        if (url == null) return null;
        if (url.contains("file=")) {
            return url.substring(url.indexOf("file=") + 5);
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }

    /**
     * Work out the expiry timestamp from the chosen option.
     *
     * <p>A past date is rejected rather than accepted, because silently creating a
     * link that is already dead is the kind of thing a user reports as "the share
     * button is broken" three days later.
     */
    private LocalDateTime resolveExpiry(ShareLinkCreateRequest request) {
        ShareDuration duration = request.getDuration();
        if (duration == null) {
            throw new BadRequestException("A share duration is required");
        }

        if (duration == ShareDuration.PERMANENT) {
            return null;
        }

        if (duration == ShareDuration.CUSTOM) {
            LocalDateTime custom = request.getCustomExpiresAt();
            if (custom == null) {
                throw new BadRequestException("A custom expiry date and time is required for a custom duration");
            }
            if (!custom.isAfter(LocalDateTime.now())) {
                throw new BadRequestException("The expiry date and time must be in the future");
            }
            return custom;
        }

        Duration fixed = duration.getDuration();
        if (fixed == null) {
            throw new BadRequestException("Unsupported share duration: " + duration);
        }
        return LocalDateTime.now().plus(fixed);
    }

    /** Human-readable expiry for the audit description. Never includes a token. */
    private String describeExpiry(LocalDateTime expiresAt) {
        return expiresAt == null ? "never" : expiresAt.toString();
    }

    /**
     * A token nobody can guess.
     *
     * <p>32 bytes straight from {@link SecureRandom}, encoded URL-safe and without
     * padding so it drops into an address bar unescaped. Nothing about the
     * achievement, the user or the clock contributes to it — a token derived from
     * an id or a timestamp is a token an attacker can walk.
     *
     * <p>The uniqueness retry will realistically never run. A collision among 256
     * random bits is not something that happens, but the check costs one indexed
     * lookup and turns an impossible catastrophe into an impossible retry.
     */
    private String generateUniqueToken() {
        for (int attempt = 0; attempt < TOKEN_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[TOKEN_BYTES];
            SECURE_RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            if (!shareLinkRepository.existsByShareToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to generate a unique share token");
    }

    /**
     * The full address to hand out for a token.
     *
     * <p>Built from the configured base URL, so it is right in local development
     * and in production without a code change, and so it can never be assembled
     * out of an attacker-supplied Host header.
     */
    private String buildShareUrl(String token) {
        String base = shareBaseUrl == null ? "" : shareBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/public/share.html?t=" + token;
    }
}
