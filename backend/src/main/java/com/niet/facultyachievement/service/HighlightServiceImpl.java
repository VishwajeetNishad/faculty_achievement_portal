package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.HighlightMetadataRequest;
import com.niet.facultyachievement.dto.HighlightResponse;
import com.niet.facultyachievement.dto.publicview.PublicHighlightResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.HighlightFocalPoint;
import com.niet.facultyachievement.entity.HomepageHighlight;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.HomepageHighlightRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    /**
     * Keeps audit descriptions readable. Alt text can run to 255 characters and
     * the description column holds 500, so a long alt text plus a prefix would
     * still fit — but a one-line trail is easier to scan than a wall of prose.
     */
    private static final int AUDIT_LABEL_MAX = 120;

    private final HomepageHighlightRepository highlightRepository;
    private final HighlightImageStorageService imageStorage;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /* ================================================================
       Reads
       ================================================================ */

    @Override
    @Transactional(readOnly = true)
    public List<PublicHighlightResponse> listPublic() {
        return highlightRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()
                .stream()
                .map(PublicHighlightResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HighlightResponse> listAll() {
        return highlightRepository.findAllWithUploader()
                .stream()
                .map(HighlightResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HighlightImage loadImage(Long id) {
        HomepageHighlight highlight = highlightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Highlight image not found."));

        /* An inactive highlight is not merely unlisted — its image stops being
           downloadable. Otherwise "retire this banner" would only remove the link
           to it, and anyone who had the URL (or a search engine that indexed it)
           would keep seeing the withdrawn poster. Same 404 as a missing row, so
           the response does not reveal that a retired highlight exists. */
        if (!highlight.isActive()) {
            throw new ResourceNotFoundException("Highlight image not found.");
        }

        return new HighlightImage(
                imageStorage.loadAsResource(highlight.getStoredFilename()),
                highlight.getContentType(),
                highlight.getFileSizeBytes());
    }

    /* ================================================================
       Writes
       ================================================================ */

    @Override
    @Transactional
    public HighlightResponse create(MultipartFile file, HighlightMetadataRequest request, String actorEmail) {
        User actor = loadActor(actorEmail);

        String linkUrl = normaliseLinkUrl(request.getLinkUrl());

        /* The file is validated and written before the row is built, so a
           rejected image never creates a database row. The reverse order would
           leave an orphan row pointing at nothing whenever validation failed. */
        HighlightImageStorageService.StoredImage stored = imageStorage.store(file);

        HomepageHighlight highlight = HomepageHighlight.builder()
                .storedFilename(stored.filename())
                .contentType(stored.contentType())
                .fileSizeBytes(stored.sizeBytes())
                .imageWidth(stored.width())
                .imageHeight(stored.height())
                .altText(request.getAltText().trim())
                .caption(blankToNull(request.getCaption()))
                .linkUrl(linkUrl)
                .focalPoint(request.getFocalPoint() == null
                        ? HighlightFocalPoint.CENTER
                        : request.getFocalPoint())
                // New banners go live unless the administrator said otherwise.
                .active(request.getActive() == null || request.getActive())
                // Lands at the end of the carousel, never jumping ahead of the
                // slides somebody already arranged.
                .displayOrder(highlightRepository.findMaxDisplayOrder() + 1)
                .uploadedBy(actor)
                .build();

        /* saveAndFlush, not save. @UpdateTimestamp is applied by Hibernate as the
           statement is flushed, and the response's ?v= token is built from that
           timestamp. Deferring the flush to commit time would mean this method
           returns the value the row had BEFORE the change, so the caller would be
           handed a stale cache-busting token. */
        HomepageHighlight saved = highlightRepository.saveAndFlush(highlight);

        /* If the transaction rolls back after this point the row disappears but
           the file stays. That leaves an unreferenced file on disk, which is
           harmless housekeeping — the opposite mistake, deleting a file a
           surviving row points at, would break the homepage. */
        auditLogService.logAction(AuditAction.HIGHLIGHT_CREATED, "HOMEPAGE_HIGHLIGHT", saved.getId(),
                "Uploaded homepage highlight: " + label(saved.getAltText())
                        + " (" + saved.getImageWidth() + "x" + saved.getImageHeight() + ")",
                actor, null);

        return HighlightResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public HighlightResponse updateMetadata(Long id, HighlightMetadataRequest request, String actorEmail) {
        User actor = loadActor(actorEmail);
        HomepageHighlight highlight = requireHighlight(id);

        highlight.setAltText(request.getAltText().trim());
        highlight.setCaption(blankToNull(request.getCaption()));
        highlight.setLinkUrl(normaliseLinkUrl(request.getLinkUrl()));

        // Null means "leave it alone" on update, so editing a caption cannot
        // silently publish a poster somebody deliberately retired.
        if (request.getFocalPoint() != null) {
            highlight.setFocalPoint(request.getFocalPoint());
        }
        if (request.getActive() != null) {
            highlight.setActive(request.getActive());
        }

        HomepageHighlight saved = highlightRepository.saveAndFlush(highlight);

        auditLogService.logAction(AuditAction.HIGHLIGHT_UPDATED, "HOMEPAGE_HIGHLIGHT", saved.getId(),
                "Updated homepage highlight: " + label(saved.getAltText())
                        + " (" + (saved.isActive() ? "live" : "retired") + ")",
                actor, null);

        return HighlightResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public HighlightResponse replaceImage(Long id, MultipartFile file, String actorEmail) {
        User actor = loadActor(actorEmail);
        HomepageHighlight highlight = requireHighlight(id);

        String previousFilename = highlight.getStoredFilename();

        HighlightImageStorageService.StoredImage stored = imageStorage.store(file);

        highlight.setStoredFilename(stored.filename());
        highlight.setContentType(stored.contentType());
        highlight.setFileSizeBytes(stored.sizeBytes());
        highlight.setImageWidth(stored.width());
        highlight.setImageHeight(stored.height());

        /* Flushed here for the same reason as create(): the ?v= token in the
           response is @UpdateTimestamp's new value, and this is the one endpoint
           where a stale token would be visible — the administrator would replace
           the poster and their browser would keep showing the old one from cache. */
        HomepageHighlight saved = highlightRepository.saveAndFlush(highlight);

        /* The old file goes only after the new row is committed. Deleting it now
           would mean a rollback leaves the row pointing at a file that no longer
           exists — a broken image on the front page, and unrecoverable. */
        deleteFileAfterCommit(previousFilename);

        auditLogService.logAction(AuditAction.HIGHLIGHT_UPDATED, "HOMEPAGE_HIGHLIGHT", saved.getId(),
                "Replaced image for homepage highlight: " + label(saved.getAltText())
                        + " (" + saved.getImageWidth() + "x" + saved.getImageHeight() + ")",
                actor, null);

        return HighlightResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public List<HighlightResponse> reorder(List<Long> orderedIds, String actorEmail) {
        User actor = loadActor(actorEmail);

        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new BadRequestException("The reordered list of highlight ids is required.");
        }

        Set<Long> submitted = new HashSet<>(orderedIds);
        if (submitted.size() != orderedIds.size()) {
            throw new BadRequestException("The reordered list contains the same highlight twice.");
        }

        List<HomepageHighlight> stored = highlightRepository.findAllWithUploader();
        Set<Long> storedIds = new HashSet<>();
        for (HomepageHighlight h : stored) {
            storedIds.add(h.getId());
        }

        /* Set equality, not "every submitted id exists". A list that merely
           omitted a row would leave that row at its old position while every
           other row moved — the carousel would end up in an order nobody chose,
           and nobody would know why. Rejecting is the only safe answer. */
        if (!storedIds.equals(submitted)) {
            throw new BadRequestException(
                    "The new order must list every highlight exactly once. The page is out of date — "
                            + "please reload it and try again.");
        }

        List<HomepageHighlight> reordered = new ArrayList<>(stored.size());
        for (int i = 0; i < orderedIds.size(); i++) {
            Long wantedId = orderedIds.get(i);
            for (HomepageHighlight h : stored) {
                if (h.getId().equals(wantedId)) {
                    h.setDisplayOrder(i + 1);
                    reordered.add(h);
                    break;
                }
            }
        }

        List<HomepageHighlight> saved = highlightRepository.saveAllAndFlush(reordered);

        auditLogService.logAction(AuditAction.HIGHLIGHT_UPDATED, "HOMEPAGE_HIGHLIGHT", null,
                "Reordered the homepage carousel (" + saved.size() + " banners)",
                actor, null);

        return saved.stream().map(HighlightResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public void delete(Long id, String actorEmail) {
        User actor = loadActor(actorEmail);
        HomepageHighlight highlight = requireHighlight(id);

        String filename = highlight.getStoredFilename();
        String deletedLabel = label(highlight.getAltText());

        highlightRepository.delete(highlight);

        // Row first, file after commit — same reasoning as replaceImage.
        deleteFileAfterCommit(filename);

        auditLogService.logAction(AuditAction.HIGHLIGHT_DELETED, "HOMEPAGE_HIGHLIGHT", id,
                "Deleted homepage highlight: " + deletedLabel,
                actor, null);
    }

    /* ================================================================
       Helpers
       ================================================================ */

    /**
     * The acting user, resolved from the email the controller read out of the
     * security context. Never from a request body — an actor supplied by the
     * caller is an actor the caller chose.
     */
    private User loadActor(String actorEmail) {
        return userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private HomepageHighlight requireHighlight(Long id) {
        return highlightRepository.findByIdWithUploader(id)
                .orElseThrow(() -> new ResourceNotFoundException("Highlight not found with id: " + id));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Accepts an absolute {@code http}/{@code https} URL or a site-relative path,
     * and nothing else.
     *
     * <p>This is the check that keeps a {@code javascript:} URL off the
     * institution's front page. Anyone holding {@code MANAGE_HIGHLIGHTS} can set
     * this field, and the value ends up in an {@code href} on the most-visited
     * page in the portal — so a marketing permission must not become a scripting
     * permission. {@code //evil.example} is rejected too: browsers read it as
     * protocol-relative, so it would leave the site while looking like a path.
     *
     * <p>The browser re-checks the same rule before rendering the link. Two
     * checks, because only one of them is the one that matters and it is this one.
     */
    private static String normaliseLinkUrl(String rawUrl) {
        String url = blankToNull(rawUrl);
        if (url == null) {
            return null;
        }

        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/") && !url.startsWith("//")) {
            return url;
        }

        throw new BadRequestException(
                "The link must start with http://, https:// or / — other kinds of link "
                        + "(such as javascript:) are not allowed on the home page.");
    }

    private static String label(String altText) {
        if (altText == null) return "(untitled)";
        return altText.length() <= AUDIT_LABEL_MAX
                ? altText
                : altText.substring(0, AUDIT_LABEL_MAX) + "…";
    }

    /**
     * Remove a file once the surrounding transaction has actually committed.
     *
     * <p>Registered as a transaction synchronisation rather than called inline so
     * that a rollback — a constraint violation, a lost connection — cannot leave
     * a surviving row pointing at a file that has already been erased. Deletion
     * is best-effort by design: an orphaned file is housekeeping, a missing one
     * is a broken homepage.
     */
    private void deleteFileAfterCommit(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No transaction in progress (only reachable if a caller drops
            // @Transactional), so there is nothing to wait for.
            imageStorage.delete(filename);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorage.delete(filename);
            }
        });
    }
}
