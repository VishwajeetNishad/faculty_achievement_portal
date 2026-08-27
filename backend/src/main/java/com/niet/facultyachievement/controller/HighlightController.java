package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.HighlightMetadataRequest;
import com.niet.facultyachievement.dto.HighlightOrderRequest;
import com.niet.facultyachievement.dto.HighlightResponse;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.service.HighlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages the banners in the public home page carousel.
 *
 * <p>Every method is gated by {@code MANAGE_HIGHLIGHTS} (or the Admin role), and
 * every method takes the acting user from the injected {@link Authentication} —
 * i.e. from the JWT, via the security context. No endpoint here accepts an actor
 * in its body, because an actor the caller supplies is an actor the caller chose.
 *
 * <p>{@code MANAGE_HIGHLIGHTS} is deliberately delegable to a communications
 * person rather than reserved for Admins: it changes marketing images and grants
 * no further authority. It is therefore absent from
 * {@code Permissions.ADMIN_ONLY_GRANTABLE}.
 *
 * <p><strong>Uploads are validated on the server, always.</strong> The admin page
 * pre-checks the file type and size to give a fast error message, but
 * {@code HighlightImageStorageService} re-inspects the bytes and ignores both the
 * submitted filename and the {@code Content-Type} header. Nothing the browser
 * says about a file is trusted.
 */
@RestController
@RequestMapping("/api/highlights")
@RequiredArgsConstructor
public class HighlightController {

    private final HighlightService highlightService;

    /** GET /api/highlights — every banner, live and retired, for the admin table. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_HIGHLIGHTS + "')")
    public ResponseEntity<List<HighlightResponse>> getAllHighlights() {
        return ResponseEntity.ok(highlightService.listAll());
    }

    /**
     * POST /api/highlights — upload a new banner. Multipart: {@code file} plus the
     * metadata fields.
     *
     * <p>The metadata arrives via {@code @ModelAttribute} rather than
     * {@code @RequestBody} because a multipart request cannot also carry a JSON
     * body. {@code @Valid} still applies, and a binding or validation failure
     * raises {@code MethodArgumentNotValidException}, which the global handler
     * turns into a 400 listing the offending fields.
     *
     * <p>New banners land at the end of the carousel, so an upload never displaces
     * the slide somebody deliberately put first.
     *
     * <p>{@code required = false} on the file is not laxness. Spring's resolver
     * answers a missing {@code MultipartFile} with
     * {@code MissingServletRequestPartException}, and letting the service's own
     * "No image file was submitted." check run instead produces a 400 an
     * administrator can act on rather than a bare failure.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_HIGHLIGHTS + "')")
    public ResponseEntity<HighlightResponse> createHighlight(
            Authentication authentication,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Valid @ModelAttribute HighlightMetadataRequest request) {

        HighlightResponse created =
                highlightService.create(file, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/highlights/{id} — edit the text, focal point or live/retired state.
     *
     * <p>JSON, not multipart: fixing a typo in a caption should not require
     * re-uploading the poster.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_HIGHLIGHTS + "')")
    public ResponseEntity<HighlightResponse> updateHighlight(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody HighlightMetadataRequest request) {

        HighlightResponse updated =
                highlightService.updateMetadata(id, request, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/highlights/{id}/image — swap the image, keeping the row's text and
     * position.
     *
     * <p>A {@code POST} on a sub-resource rather than a multipart {@code PUT} for a
     * practical reason: the frontend's {@code ApiClient.upload()} already does
     * multipart {@code POST} correctly, including leaving the boundary to the
     * browser, so this needs no new client code and no hand-built
     * {@code Content-Type}.
     */
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_HIGHLIGHTS + "')")
    public ResponseEntity<HighlightResponse> replaceHighlightImage(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        HighlightResponse updated =
                highlightService.replaceImage(id, file, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/highlights/order — set the whole carousel order in one call.
     *
     * <p>Takes the complete id list, not a "move up" instruction. Two single-step
     * calls can interleave — two administrators reordering at once, or one
     * clicking ▲ twice quickly — and leave an order neither of them chose. One
     * atomic call cannot.
     *
     * <p>Rejected with 400 unless the submitted ids are exactly the stored ids, so
     * a stale page cannot silently strand a row.
     */
    @PutMapping("/order")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_HIGHLIGHTS + "')")
    public ResponseEntity<List<HighlightResponse>> reorderHighlights(
            Authentication authentication,
            @Valid @RequestBody HighlightOrderRequest request) {

        return ResponseEntity.ok(
                highlightService.reorder(request.getOrderedIds(), authentication.getName()));
    }

    /**
     * DELETE /api/highlights/{id} — remove a banner and its file.
     *
     * <p>Permanent. To take a poster off the homepage while keeping it, set
     * {@code active} to false through the PUT above instead.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_HIGHLIGHTS + "')")
    public ResponseEntity<Void> deleteHighlight(
            Authentication authentication,
            @PathVariable("id") Long id) {

        highlightService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
