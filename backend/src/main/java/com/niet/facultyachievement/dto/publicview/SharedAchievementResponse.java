package com.niet.facultyachievement.dto.publicview;

import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.ShareLink;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One achievement as the holder of a share link sees it.
 *
 * <p>This is the only public response that can describe a record which is
 * <em>not</em> APPROVED and PUBLIC. That is the entire point of the feature: a
 * faculty member with work still under review needs to show it to an external
 * reviewer, a funding body or a collaborator who has no account here. The
 * owner made that choice explicitly, for one record, with an expiry.
 *
 * <p><strong>What it deliberately does not say: whether the record was
 * approved.</strong> No status, no visibility, no reviewer comment, no approver
 * name, no verification date. Two reasons. First, the owner shared their
 * <em>work</em>; the department's internal opinion of it is not theirs to hand
 * out, and "REJECTED by the department" reaching a funding body would be a
 * disclosure nobody asked for. Second, verification state is internal workflow
 * and simply is not part of what a share link is for.
 *
 * <p>Contrast this with {@link PublicAchievementResponse}, which <em>does</em>
 * carry status and visibility — there they are always APPROVED and PUBLIC by
 * construction, so they say nothing, and the browser's defensive re-check needs
 * them. Here they would actually reveal something, so they are gone.
 *
 * <p>A consequence worth stating plainly: any of the owner's records can be
 * shared, whatever its status. Restricting sharing to approved work would be a
 * policy this project never agreed, and it would break the stated purpose of
 * showing unpublished research. Nothing is shared unless the owner asks for a
 * link, and the link dies on expiry or on one click.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedAchievementResponse {

    /* ── Who did the work ─────────────────────────────────────────────── */
    private String facultyName;
    private String designation;
    private String departmentCode;
    private String departmentName;

    /**
     * The author's public profile address, or {@code null} if they have none.
     * Only useful for an optional "see their public work" link; it exposes
     * nothing that {@code /api/public/faculty} does not already publish.
     */
    private String facultySlug;

    /* ── The work itself ──────────────────────────────────────────────── */
    private String categoryCode;
    private String categoryName;
    private String title;
    private String description;
    private String keywords;
    private LocalDate achievementDate;
    private String academicYear;

    private PublicAchievementResponse.PublicationDetail publication;
    private PublicAchievementResponse.PatentDetail patent;
    private PublicAchievementResponse.ResearchGrantDetail researchGrant;
    private PublicAchievementResponse.WorkshopFdpDetail workshopFdp;
    private PublicAchievementResponse.AwardDetail award;

    /* ── The link this record arrived through ─────────────────────────── */

    /** When the link was created, so a visitor can see how fresh it is. */
    private LocalDateTime sharedAt;

    /**
     * When the link stops working, or {@code null} for a permanent link.
     *
     * <p>Sent so the page can show "expires in 3 days". That countdown is
     * <strong>decoration</strong>. The server re-reads this value and re-judges
     * it on every single request, including the request for the proof document.
     * A visitor who leaves the tab open past the expiry, or edits the countdown
     * in the browser console, gets a 410 on their next call like anybody else.
     */
    private LocalDateTime expiresAt;

    /** Convenience flag for the page: {@code expiresAt == null}. */
    private boolean permanent;

    /**
     * Whether the proof PDF is reachable at
     * {@code /api/public/share/{token}/document}.
     *
     * <p>A flag, never a URL or a filename. Sharing a record is not the same as
     * sharing the evidence file behind it, so this is off unless the owner
     * ticked the box, and the physical storage path is never exposed in any
     * form.
     */
    private boolean proofDocumentAvailable;

    /**
     * Build the response for a live link.
     *
     * <p>Assumes the caller has already established that the link is neither
     * revoked nor expired. As with the other public DTOs, the decision of
     * whether to show anything lives in the service, in one place, and never
     * here.
     */
    public static SharedAchievementResponse fromEntity(ShareLink link, Achievement achievement) {
        if (link == null || achievement == null) return null;

        var user = achievement.getUser();
        var category = achievement.getCategory();
        var department = user != null ? user.getDepartment() : null;

        boolean proofAvailable = link.isIncludeProofDocument()
                && achievement.getProofDocumentUrl() != null
                && !achievement.getProofDocumentUrl().isBlank();

        return SharedAchievementResponse.builder()
                .facultyName(user != null ? user.getFullName() : null)
                .designation(user != null ? user.getDesignation() : null)
                .departmentCode(department != null ? department.getCode() : null)
                .departmentName(department != null ? department.getName() : null)
                .facultySlug(user != null ? user.getPublicSlug() : null)
                .categoryCode(category != null ? category.getCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .title(achievement.getTitle())
                .description(achievement.getDescription())
                .keywords(achievement.getKeywords())
                .achievementDate(achievement.getAchievementDate())
                .academicYear(achievement.getAcademicYear())
                .publication(PublicAchievementResponse.PublicationDetail.from(achievement.getPublication()))
                .patent(PublicAchievementResponse.PatentDetail.from(achievement.getPatent()))
                .researchGrant(PublicAchievementResponse.ResearchGrantDetail.from(achievement.getResearchGrant()))
                .workshopFdp(PublicAchievementResponse.WorkshopFdpDetail.from(achievement.getWorkshopFdp()))
                .award(PublicAchievementResponse.AwardDetail.from(achievement.getAward()))
                .sharedAt(link.getCreatedAt())
                .expiresAt(link.getExpiresAt())
                .permanent(link.getExpiresAt() == null)
                .proofDocumentAvailable(proofAvailable)
                .build();
    }
}
