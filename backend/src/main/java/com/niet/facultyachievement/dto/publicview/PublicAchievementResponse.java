package com.niet.facultyachievement.dto.publicview;

import com.niet.facultyachievement.entity.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One achievement as an anonymous visitor sees it.
 *
 * <p><strong>Why this class exists at all.</strong> The portal already has
 * {@code AchievementResponse}, and reusing it here would have saved a few
 * hundred lines. It would also have published the reviewer's private comment,
 * the faculty member's email address, their employee id, the internal user id
 * and a link to the proof PDF — every single one of those is a field on that
 * DTO. A reviewer writing "reject, the impact factor looks inflated" does not
 * expect a student to read it.
 *
 * <p>So this is a deliberate, separate, additive-only class. The safety
 * property is structural rather than procedural: there is no line of code
 * anywhere that <em>could</em> put a sensitive field into a public response,
 * because no such field exists on the object being serialised. That holds even
 * if somebody later adds a column to {@code Achievement} and forgets about the
 * public API — the new column simply will not appear.
 *
 * <p>Deliberately absent, and each for a reason:
 * <ul>
 *   <li>{@code verificationComment} — the reviewer's internal note;</li>
 *   <li>{@code facultyEmail}, {@code employeeId}, {@code phone} — personal
 *       contact details, and an employee id is an internal identifier;</li>
 *   <li>{@code proofDocumentUrl} — the evidence PDF stays behind an
 *       authorisation check;</li>
 *   <li>{@code verifiedByUserId} / {@code verifiedByName} / {@code verifiedAt}
 *       — who approved what, and when, is internal workflow;</li>
 *   <li>{@code userId} and the achievement's own {@code id} — internal
 *       numbering. The public site addresses people by slug, so nothing here
 *       needs a database id, and leaving them out means a visitor cannot count
 *       records or probe for the next one along.</li>
 * </ul>
 *
 * <p>{@code status} and {@code visibility} <em>are</em> included, which looks
 * odd on a public DTO until you see why: the front end runs its own defensive
 * {@code status === 'APPROVED' && visibility === 'PUBLIC'} filter over whatever
 * arrives. That check is belt-and-braces — the service already guarantees it —
 * but it only works if the fields are present, and a filter that silently
 * passes everything because it is reading {@code undefined} is worse than no
 * filter. Publishing these two values leaks nothing: on a public endpoint they
 * are always {@code APPROVED} and {@code PUBLIC} by construction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicAchievementResponse {

    /** Whose work this is — slug and display name only. */
    private String facultySlug;
    private String facultyName;
    private String designation;
    private String departmentCode;
    private String departmentName;

    private String categoryCode;
    private String categoryName;

    private String title;

    /** Serves as the public abstract; it is the existing description column. */
    private String description;

    /** The author's own comma-separated subject terms. Drives public search. */
    private String keywords;

    private LocalDate achievementDate;
    private String academicYear;

    /**
     * Always {@code APPROVED} / {@code PUBLIC} on this endpoint. Present so the
     * browser's own re-check has something real to test — see the class note.
     */
    private AchievementStatus status;
    private AchievementVisibility visibility;

    /* Exactly one of these five is non-null, matching the achievement's
       category. Same field names as the signed-in portal uses, so the public
       pages render them with the existing shared helpers. */
    private PublicationDetail publication;
    private PatentDetail patent;
    private ResearchGrantDetail researchGrant;
    private WorkshopFdpDetail workshopFdp;
    private AwardDetail award;

    /* ================================================================
       Category-specific public detail.

       Each of these mirrors its entity minus anything internal. Note what
       is missing from PublicationDetail in particular: no isbnIssn. An
       ISSN is public information, but it is not something a visitor
       browsing research needs, and the smallest useful payload is the
       right default for an endpoint with no authentication in front of it.
       ================================================================ */

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PublicationDetail {
        private PublicationType publicationType;
        private String journalConferenceName;
        private String publisher;

        /**
         * A DOI is a public, citable identifier, so exposing it is correct and
         * useful — the front end turns it into a {@code doi.org} link.
         *
         * <p>It links <em>out</em>. The paper itself is never hosted or proxied
         * by this portal, because the publisher normally holds the copyright.
         */
        private String doi;

        private String volume;
        private String issue;
        private String pages;
        private BigDecimal impactFactor;
        private PublicationIndexing indexing;

        static PublicationDetail from(Publication p) {
            if (p == null) return null;
            return PublicationDetail.builder()
                    .publicationType(p.getPublicationType())
                    .journalConferenceName(p.getJournalConferenceName())
                    .publisher(p.getPublisher())
                    .doi(p.getDoi())
                    .volume(p.getVolume())
                    .issue(p.getIssue())
                    .pages(p.getPages())
                    .impactFactor(p.getImpactFactor())
                    .indexing(p.getIndexing())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PatentDetail {
        private String patentNumber;
        private PatentStatus patentStatus;
        private String country;
        private LocalDate filingDate;
        private LocalDate grantDate;

        static PatentDetail from(Patent p) {
            if (p == null) return null;
            return PatentDetail.builder()
                    .patentNumber(p.getPatentNumber())
                    .patentStatus(p.getPatentStatus())
                    .country(p.getCountry())
                    .filingDate(p.getFilingDate())
                    .grantDate(p.getGrantDate())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResearchGrantDetail {
        private String fundingAgency;
        private String projectTitle;

        /**
         * Grant amounts are published in annual reports and NAAC/NBA filings,
         * so this is already public information for a sanctioned grant.
         */
        private BigDecimal grantAmount;

        private ProjectType projectType;
        private Integer durationMonths;
        private GrantStatus grantStatus;

        static ResearchGrantDetail from(ResearchGrant g) {
            if (g == null) return null;
            return ResearchGrantDetail.builder()
                    .fundingAgency(g.getFundingAgency())
                    .projectTitle(g.getProjectTitle())
                    .grantAmount(g.getGrantAmount())
                    .projectType(g.getProjectType())
                    .durationMonths(g.getDurationMonths())
                    .grantStatus(g.getGrantStatus())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkshopFdpDetail {
        private String eventName;
        private EventType eventType;
        private EventRole role;
        private String location;
        private Integer durationDays;
        private String organizingBody;

        static WorkshopFdpDetail from(WorkshopFdp w) {
            if (w == null) return null;
            return WorkshopFdpDetail.builder()
                    .eventName(w.getEventName())
                    .eventType(w.getEventType())
                    .role(w.getRole())
                    .location(w.getLocation())
                    .durationDays(w.getDurationDays())
                    .organizingBody(w.getOrganizingBody())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AwardDetail {
        private String awardName;
        private String awardingBody;
        private AwardLevel awardLevel;

        static AwardDetail from(Award a) {
            if (a == null) return null;
            return AwardDetail.builder()
                    .awardName(a.getAwardName())
                    .awardingBody(a.getAwardingBody())
                    .awardLevel(a.getAwardLevel())
                    .build();
        }
    }

    /**
     * Map an achievement for public consumption.
     *
     * <p>This method does <strong>not</strong> check whether the record is
     * allowed to be public. That is on purpose: mixing "may I show this?" with
     * "how do I show this?" is how a filter ends up being applied in three
     * places and forgotten in a fourth. The rule lives in exactly one place,
     * {@code PublicDiscoveryServiceImpl}, which never hands anything to this
     * method unless it is already APPROVED and PUBLIC.
     */
    public static PublicAchievementResponse fromEntity(Achievement achievement) {
        if (achievement == null) return null;

        var user = achievement.getUser();
        var category = achievement.getCategory();
        var department = user != null ? user.getDepartment() : null;

        return PublicAchievementResponse.builder()
                .facultySlug(user != null ? user.getPublicSlug() : null)
                .facultyName(user != null ? user.getFullName() : null)
                .designation(user != null ? user.getDesignation() : null)
                .departmentCode(department != null ? department.getCode() : null)
                .departmentName(department != null ? department.getName() : null)
                .categoryCode(category != null ? category.getCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .title(achievement.getTitle())
                .description(achievement.getDescription())
                .keywords(achievement.getKeywords())
                .achievementDate(achievement.getAchievementDate())
                .academicYear(achievement.getAcademicYear())
                .status(achievement.getStatus())
                .visibility(achievement.getVisibility())
                .publication(PublicationDetail.from(achievement.getPublication()))
                .patent(PatentDetail.from(achievement.getPatent()))
                .researchGrant(ResearchGrantDetail.from(achievement.getResearchGrant()))
                .workshopFdp(WorkshopFdpDetail.from(achievement.getWorkshopFdp()))
                .award(AwardDetail.from(achievement.getAward()))
                .build();
    }
}
