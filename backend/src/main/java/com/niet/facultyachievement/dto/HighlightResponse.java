package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.HighlightFocalPoint;
import com.niet.facultyachievement.entity.HomepageHighlight;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One home page banner as the administrator who manages it sees it.
 *
 * <p>The richer sibling of
 * {@link com.niet.facultyachievement.dto.publicview.PublicHighlightResponse}: it
 * carries the id needed to edit the row, the ordering and {@code active} flags
 * the admin screen manipulates, the file facts that let the screen warn about a
 * poster being too small, and the uploader's name for accountability.
 *
 * <p>{@code storedFilename} is still absent. Even an administrator has no use for
 * the on-disk name — every operation is by id — and keeping it out of every
 * response means the uploads directory layout is never described to a browser,
 * so an XSS bug elsewhere in the admin area could not read it out of a rendered
 * table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighlightResponse {

    private Long id;

    /**
     * Same API-base-relative form as the public DTO, so the admin table's
     * thumbnail and the visitor's slide are provably the same image. Points at
     * the public endpoint on purpose: if a thumbnail renders in the admin table,
     * the slide will render on the home page.
     */
    private String imagePath;

    private String altText;
    private String caption;
    private String linkUrl;
    private HighlightFocalPoint focalPoint;

    private Integer displayOrder;
    private Boolean active;

    /** Shown so the admin can see at a glance which posters are heavy. */
    private Long fileSizeBytes;

    /** The format the server detected, not what was uploaded as. */
    private String contentType;

    /**
     * The admin screen compares {@code imageWidth} against the recommended
     * 1600px and warns when a poster will be upscaled and look soft.
     */
    private Integer imageWidth;
    private Integer imageHeight;

    /** Name only — never the uploader's email, id or department. */
    private String uploadedByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Requires {@code uploadedBy} to be loaded. Callers use
     * {@code findAllWithUploader()} / {@code findByIdWithUploader()} so the name
     * does not cost one query per row.
     */
    public static HighlightResponse fromEntity(HomepageHighlight highlight) {
        if (highlight == null) return null;

        long version = highlight.getUpdatedAt() == null
                ? 0L
                : highlight.getUpdatedAt().toEpochSecond(ZoneOffset.UTC);

        return HighlightResponse.builder()
                .id(highlight.getId())
                .imagePath("/public/highlights/" + highlight.getId() + "/image?v=" + version)
                .altText(highlight.getAltText())
                .caption(highlight.getCaption())
                .linkUrl(highlight.getLinkUrl())
                .focalPoint(highlight.getFocalPoint())
                .displayOrder(highlight.getDisplayOrder())
                .active(highlight.isActive())
                .fileSizeBytes(highlight.getFileSizeBytes())
                .contentType(highlight.getContentType())
                .imageWidth(highlight.getImageWidth())
                .imageHeight(highlight.getImageHeight())
                .uploadedByName(highlight.getUploadedBy() == null
                        ? null
                        : highlight.getUploadedBy().getFullName())
                .createdAt(highlight.getCreatedAt())
                .updatedAt(highlight.getUpdatedAt())
                .build();
    }
}
