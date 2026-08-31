package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One banner in the public home page carousel.
 *
 * <p>A highlight is a picture the institution chose to put on its front page —
 * an award ceremony, a lab inauguration, a conference appearance. It is
 * <strong>marketing content, not an achievement record</strong>, and that
 * distinction is the reason this entity exists at all rather than reusing
 * {@link Achievement}.
 *
 * <p>An achievement is a claim made by a named faculty member, backed by a proof
 * document, approved by their HOD, and counted in every public statistic. A
 * highlight has none of those properties. Filing an award poster as an award
 * record would raise the public "Awards" figure without anybody having submitted
 * anything, which would break the portal's central promise that every number on
 * the public site traces back to a verified submission. So highlights live in
 * their own table, are served by their own endpoint, and contribute to no count
 * anywhere.
 *
 * <p><strong>The image bytes are not in this row.</strong> Only
 * {@link #storedFilename} is — a UUID naming a file in the highlights upload
 * directory, handled by {@code HighlightImageStorageService} exactly as
 * achievement proof PDFs are. The submitted filename is never stored or trusted.
 */
@Entity
@Table(name = "homepage_highlights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomepageHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A UUID plus extension, inside the highlights upload directory. */
    @Column(name = "stored_filename", nullable = false, length = 120)
    private String storedFilename;

    /**
     * The format the server detected from the file's magic bytes — never the
     * {@code Content-Type} the browser claimed.
     */
    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /**
     * Read from the image header at upload time. Sent to the browser so the
     * {@code <img>} can reserve its space before the file arrives, and used by
     * the admin screen to warn when a poster is too small for the frame.
     */
    @Column(name = "image_width", nullable = false)
    private int imageWidth;

    @Column(name = "image_height", nullable = false)
    private int imageHeight;

    /**
     * Required. A homepage banner with no alt text is invisible to a screen
     * reader, and this doubles as the row's label in the admin list.
     */
    @Column(name = "alt_text", nullable = false, length = 255)
    private String altText;

    /** Optional caption under the slide; these posters usually carry their own text. */
    @Column(name = "caption", length = 160)
    private String caption;

    /**
     * Optional click-through. Only {@code http}, {@code https} or a
     * site-relative path survives validation in the service — a
     * {@code javascript:} URL here would become a clickable script on the
     * institution's front page.
     */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "focal_point", nullable = false, length = 20)
    private HighlightFocalPoint focalPoint;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Whether the slide is live. An inactive highlight is not listed
     * <em>and</em> its image stops being downloadable, so retiring a banner
     * actually retires it rather than merely hiding the link to it.
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Who uploaded it. Always resolved from the security context, never from a
     * request body. Never exposed on the public endpoint.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Bumped on every change, and used as the {@code ?v=} token on the public
     * image URL. That token is what makes a one-year cache header safe:
     * replacing the image changes the URL, so every browser refetches it.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
