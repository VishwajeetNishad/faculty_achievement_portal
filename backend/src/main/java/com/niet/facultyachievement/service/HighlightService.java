package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.HighlightMetadataRequest;
import com.niet.facultyachievement.dto.HighlightResponse;
import com.niet.facultyachievement.dto.publicview.PublicHighlightResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages the banner images in the public home page carousel.
 *
 * <p>Highlights are institutional marketing, kept structurally apart from
 * achievement records: their own table, their own endpoint, and no contribution
 * to any public count. See
 * {@link com.niet.facultyachievement.entity.HomepageHighlight} for why that
 * separation matters.
 *
 * <p>Every write method takes {@code actorEmail}, which callers must read from
 * the security context — never from a request body.
 */
public interface HighlightService {

    /** What the public image endpoint needs in order to stream one file. */
    record HighlightImage(Resource resource, String contentType, long sizeBytes) { }

    /** Active slides in display order — the only thing a visitor ever receives. */
    List<PublicHighlightResponse> listPublic();

    /** Every slide, live or retired, for the admin table. */
    List<HighlightResponse> listAll();

    /** Store the image, then the row. The new slide lands at the end of the carousel. */
    HighlightResponse create(MultipartFile file, HighlightMetadataRequest request, String actorEmail);

    /** Text, focal point and {@code active} only. The image file is not touched. */
    HighlightResponse updateMetadata(Long id, HighlightMetadataRequest request, String actorEmail);

    /** Swap in a new image, keeping the row's text, order and position. */
    HighlightResponse replaceImage(Long id, MultipartFile file, String actorEmail);

    /**
     * Rewrite the whole carousel order in one statement.
     *
     * @throws com.niet.facultyachievement.exception.BadRequestException if the
     *         submitted ids are not exactly the stored ids
     */
    List<HighlightResponse> reorder(List<Long> orderedIds, String actorEmail);

    void delete(Long id, String actorEmail);

    /**
     * Open one highlight's image for streaming.
     *
     * @throws com.niet.facultyachievement.exception.ResourceNotFoundException if
     *         no such highlight exists <em>or it is inactive</em> — retiring a
     *         banner must stop it being downloadable, not merely stop it being
     *         listed
     */
    HighlightImage loadImage(Long id);
}
