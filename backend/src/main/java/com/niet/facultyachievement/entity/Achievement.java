package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "achievements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private AchievementCategory category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * The author's own comma-separated subject terms, used by public search.
     * Free text on purpose — see V4__visibility_and_share_links.sql for why
     * this is one column rather than a tag table.
     */
    @Column(name = "keywords", length = 500)
    private String keywords;

    @Column(name = "achievement_date", nullable = false)
    private LocalDate achievementDate;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AchievementStatus status;

    /**
     * Who may read this record. Independent of {@link #status} — a record is
     * public only when it is APPROVED <em>and</em> PUBLIC. Never null; the
     * service layer defaults it to PRIVATE when a client omits it, so an older
     * frontend that does not know about visibility cannot accidentally publish
     * anything.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private AchievementVisibility visibility;

    @Column(name = "verification_comment", columnDefinition = "TEXT")
    private String verificationComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "proof_document_url", length = 500)
    private String proofDocumentUrl;

    // Optional 1-to-1 extension mappings for category-specific details
    @OneToOne(mappedBy = "achievement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Publication publication;

    @OneToOne(mappedBy = "achievement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Patent patent;

    @OneToOne(mappedBy = "achievement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ResearchGrant researchGrant;

    @OneToOne(mappedBy = "achievement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private WorkshopFdp workshopFdp;

    @OneToOne(mappedBy = "achievement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Award award;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
