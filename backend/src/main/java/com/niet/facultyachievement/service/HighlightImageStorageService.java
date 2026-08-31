package com.niet.facultyachievement.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores the banner images shown in the public home page carousel.
 *
 * <p><strong>Why this is not part of {@link FileStorageService}.</strong> That
 * service exists to guard achievement proof documents, and every rule in it is a
 * PDF rule: the extension check, the {@code application/pdf} MIME check, the
 * {@code %PDF} magic-byte check and the {@code .pdf} filename it generates.
 * Widening it to also accept images would loosen the checks protecting evidence
 * files — the most sensitive uploads in the portal — in order to add a marketing
 * feature. So this is a separate service, with its own directory, its own size
 * cap and its own image rules. It reuses the <em>shape</em> of the proven one
 * (UUID filenames, path-traversal guard, magic-byte inspection) and none of its
 * permissiveness.
 */
public interface HighlightImageStorageService {

    /**
     * What was actually written to disk.
     *
     * <p>Every field is what the <em>server</em> determined, not what the
     * browser claimed. The submitted filename and {@code Content-Type} header
     * are treated as hints and discarded.
     */
    record StoredImage(
            String filename,
            String contentType,
            long sizeBytes,
            int width,
            int height
    ) { }

    /**
     * Validate an uploaded image and write it to the highlights directory.
     *
     * @throws com.niet.facultyachievement.exception.BadRequestException if the
     *         file is empty, over the size cap, not a PNG/JPEG/WebP by its magic
     *         bytes, or has an unreadable or absurd header
     */
    StoredImage store(MultipartFile file);

    /** Open a stored image for streaming, with the path-traversal guard applied. */
    Resource loadAsResource(String filename);

    /** Best-effort removal. Never throws: a leftover file is not worth a 500. */
    void delete(String filename);
}
