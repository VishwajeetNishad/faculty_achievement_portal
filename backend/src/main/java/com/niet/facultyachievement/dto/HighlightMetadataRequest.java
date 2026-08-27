package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.HighlightFocalPoint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * The text and display settings of a home page banner — everything about a
 * highlight except the image file itself.
 *
 * <p>Used by two endpoints, deliberately:
 *
 * <ul>
 *   <li>{@code POST /api/highlights} binds it with {@code @ModelAttribute}
 *       alongside the uploaded file, because a multipart request cannot also
 *       carry a JSON body;</li>
 *   <li>{@code PUT /api/highlights/{id}} binds it with {@code @RequestBody},
 *       because editing the caption should not require re-uploading the poster.</li>
 * </ul>
 *
 * <p>One class for both keeps the validation rules in a single place. Two classes
 * would drift, and the one that drifted would be the one with the weaker rules.
 *
 * <p><strong>Not present:</strong> {@code displayOrder} (set by the server on
 * create, and changed only through the atomic reorder endpoint), the uploader
 * (always the authenticated user), and anything about the file (all of it is
 * detected server-side from the bytes).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighlightMetadataRequest {

    /**
     * Required, because a banner with no alt text is simply missing for anyone
     * using a screen reader — and this poster is the first thing on the page.
     */
    @NotBlank(message = "Alt text is required — describe the image for screen readers")
    @Size(max = 255, message = "Alt text must not exceed 255 characters")
    private String altText;

    /**
     * Optional line shown under the slide. These posters usually contain all
     * their own text, so blank is the normal case rather than a missing value.
     */
    @Size(max = 160, message = "Caption must not exceed 160 characters")
    private String caption;

    /**
     * Optional click-through target.
     *
     * <p>No {@code @Pattern} here on purpose. The rule that matters — only
     * {@code http}, {@code https} or a site-relative path, never
     * {@code javascript:} — is enforced in {@code HighlightService}, where the
     * rejection can explain itself. A regex strict enough to be safe produces a
     * message no administrator could act on, and one loose enough to read well
     * would not be safe.
     */
    @Size(max = 500, message = "Link URL must not exceed 500 characters")
    private String linkUrl;

    /**
     * Which part of the image survives the {@code cover} crop. A closed enum, so
     * no administrator-supplied text can ever reach a CSS value.
     *
     * <p>Null means {@code CENTER} on create, and "leave as it is" on update.
     */
    private HighlightFocalPoint focalPoint;

    /**
     * Whether the slide is live.
     *
     * <p>Null means {@code true} on create — uploading a banner and having
     * nothing appear would be baffling — and "leave as it is" on update, so a
     * caption edit cannot accidentally publish a retired poster.
     */
    private Boolean active;
}
