package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementVisibility;
import com.niet.facultyachievement.entity.ShareLink;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A share link as its owner sees it, on the "My Research &amp; Shared Resources"
 * screen.
 *
 * <p>This is the only response in the portal that contains a share token, and it
 * only ever goes to the achievement's owner. The token has to be here — the
 * "Copy link" button cannot work otherwise — which is also why the token is
 * stored in plain text rather than hashed. That trade-off is recorded on
 * {@link ShareLink}.
 *
 * <p>The token must never reach an audit log, a server log, a notification or
 * any response other than this one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkResponse {

    /** Which of the owner's achievements this link points at. */
    private Long achievementId;
    private String achievementTitle;
    private String categoryCode;
    private String categoryName;

    /** The owner's current visibility setting, shown next to the link. */
    private AchievementVisibility visibility;

    /** The secret. Owner-only, by construction — see the class note. */
    private String shareToken;

    /**
     * The whole link, ready to paste.
     *
     * <p>Assembled on the server from a configured base URL rather than in the
     * browser, so the copied address is right in local development and in
     * production without a code change, and so the owner is never shown a link
     * built from whatever host happened to be in their address bar.
     */
    private String shareUrl;

    /** {@code null} for a permanent link. */
    private LocalDateTime expiresAt;
    private boolean permanent;

    private boolean includeProofDocument;

    /**
     * The link's state as one word for the UI: {@code ACTIVE}, {@code EXPIRED} or
     * {@code REVOKED}.
     *
     * <p>Computed on the server, from the server's clock. The browser is not asked
     * to work out whether a link is still alive, because the browser's answer is
     * not the one that decides anything.
     */
    private String state;

    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;

    /** How many times the link has been opened, and when last. */
    private long accessCount;
    private LocalDateTime lastAccessedAt;

    public static ShareLinkResponse fromEntity(ShareLink link, String shareUrl) {
        if (link == null) return null;

        Achievement achievement = link.getAchievement();
        var category = achievement != null ? achievement.getCategory() : null;

        String state;
        if (link.isRevoked()) {
            state = "REVOKED";
        } else if (link.isExpired()) {
            state = "EXPIRED";
        } else {
            state = "ACTIVE";
        }

        return ShareLinkResponse.builder()
                .achievementId(achievement != null ? achievement.getId() : null)
                .achievementTitle(achievement != null ? achievement.getTitle() : null)
                .categoryCode(category != null ? category.getCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .visibility(achievement != null ? achievement.getVisibility() : null)
                .shareToken(link.getShareToken())
                .shareUrl(shareUrl)
                .expiresAt(link.getExpiresAt())
                .permanent(link.getExpiresAt() == null)
                .includeProofDocument(link.isIncludeProofDocument())
                .state(state)
                .createdAt(link.getCreatedAt())
                .revokedAt(link.getRevokedAt())
                .accessCount(link.getAccessCount())
                .lastAccessedAt(link.getLastAccessedAt())
                .build();
    }
}
