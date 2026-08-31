package com.niet.facultyachievement.dto.publicview;

import com.niet.facultyachievement.entity.HighlightFocalPoint;
import com.niet.facultyachievement.entity.HomepageHighlight;
import lombok.*;

import java.time.ZoneOffset;

/**
 * One home page banner as an anonymous visitor sees it.
 *
 * <p>Like every other class in this package, it exists so that the public
 * response is a <em>different object</em> from the internal one rather than a
 * filtered view of it. Absent by construction, and each for a reason:
 *
 * <ul>
 *   <li>{@code storedFilename} — the on-disk name. Publishing it would tell a
 *       stranger the layout of the uploads directory, and the image is reachable
 *       by id anyway, so the name buys them nothing they need;</li>
 *   <li>{@code uploadedBy} — which member of staff added the banner is internal
 *       workflow, exactly as {@code verifiedByName} is on achievements;</li>
 *   <li>{@code active} — a visitor only ever receives live slides, so the flag
 *       would be the constant {@code true};</li>
 *   <li>{@code displayOrder}, {@code createdAt}, {@code updatedAt},
 *       {@code fileSizeBytes} — housekeeping the browser has no use for.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicHighlightResponse {

    /**
     * Where to fetch the image, <strong>relative to the API base</strong> — e.g.
     * {@code /public/highlights/3/image?v=1756300000}.
     *
     * <p>Deliberately not a whole URL. Two ways to build one were rejected:
     *
     * <ul>
     *   <li>A host-relative path ({@code /api/public/...}) breaks local
     *       development, where the pages are served from port 5500 and the API
     *       from 8080, so the browser would look for the image on the static
     *       server.</li>
     *   <li>An absolute URL assembled from the incoming request's {@code Host}
     *       header is the classic host-header injection footgun — a proxy or a
     *       forged header could make the server publish image links pointing at
     *       somebody else's domain.</li>
     * </ul>
     *
     * <p>So the server sends the part it actually knows and the browser prefixes
     * {@code CONFIG.API_BASE_URL}, which is already correct in both
     * environments. See {@code frontend/js/public-highlights.js}.
     *
     * <p>The {@code ?v=} token is {@code updatedAt} as epoch seconds. It is what
     * makes the endpoint's one-year cache header safe: replacing an image bumps
     * {@code updatedAt}, which changes the URL, which forces every browser to
     * refetch. Without it, a replaced banner could stay stale in visitors'
     * caches for a year.
     */
    private String imagePath;

    /** Required on every highlight, so a screen reader always has something to read. */
    private String altText;

    private String caption;

    /**
     * Already validated server-side to be {@code http}, {@code https} or a
     * site-relative path. The browser re-checks anyway — a link on the front
     * page is worth two checks.
     */
    private String linkUrl;

    /**
     * The enum <em>name</em>, e.g. {@code TOP_CENTER} — never a CSS value. The
     * browser matches it against its own list of nine and derives the class from
     * that, so no stored text can reach a stylesheet.
     */
    private HighlightFocalPoint focalPoint;

    /**
     * The real pixel size, so the {@code <img>} can carry width/height and the
     * browser reserves the slide's space before the file arrives. Without this
     * the carousel would visibly jump as each poster loads.
     */
    private Integer imageWidth;
    private Integer imageHeight;

    public static PublicHighlightResponse fromEntity(HomepageHighlight highlight) {
        if (highlight == null) return null;

        long version = highlight.getUpdatedAt() == null
                ? 0L
                : highlight.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);

        return PublicHighlightResponse.builder()
                .imagePath("/public/highlights/" + highlight.getId() + "/image?v=" + version)
                .altText(highlight.getAltText())
                .caption(highlight.getCaption())
                .linkUrl(highlight.getLinkUrl())
                .focalPoint(highlight.getFocalPoint())
                .imageWidth(highlight.getImageWidth())
                .imageHeight(highlight.getImageHeight())
                .build();
    }
}
