package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.ShareLinkCreateRequest;
import com.niet.facultyachievement.dto.ShareLinkResponse;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Share links, from the owner's side.
 *
 * <p>Base path {@code /api/achievements}, so these sit next to the achievement
 * endpoints they belong to. They are a separate class from
 * {@code AchievementController} because sharing is a separate concern with a
 * separate service, and because keeping Track B's code in its own file means the
 * existing achievement endpoints were not edited to add it.
 *
 * <p>Every method here is authenticated — {@code SecurityConfig} still ends with
 * {@code anyRequest().authenticated()} and none of these paths are whitelisted.
 * The anonymous half of sharing lives in {@code PublicController}.
 *
 * <p><strong>The owner is never taken from the request.</strong> Each method reads
 * the caller out of the {@link Authentication} that the JWT filter placed in the
 * security context, and hands that id to the service. There is no code path here
 * that lets a body or a query parameter say who you are.
 */
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final UserRepository userRepository;

    /**
     * The caller, resolved from the security context.
     *
     * <p>Same helper as {@code AchievementController} uses. The email comes from
     * the validated JWT, and the user row is re-read from the database, so a
     * deleted or renamed account cannot keep acting on an old token's say-so.
     */
    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User is not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user record not found"));
    }

    /**
     * POST /api/achievements/{id}/share — create a link, replacing any existing one.
     *
     * <p>201, because a new resource really is created. A non-owner gets 403 from
     * the service; there is no "or admin" branch, because an administrator has no
     * business publishing somebody else's unpublished research under a link that
     * names the faculty member as the sharer.
     */
    @PostMapping("/{id}/share")
    public ResponseEntity<ShareLinkResponse> createShareLink(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody ShareLinkCreateRequest request) {

        User currentUser = getAuthenticatedUser(authentication);
        ShareLinkResponse response = shareService.createShareLink(id, currentUser.getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * GET /api/achievements/{id}/share — the current link, or 204 if there is none.
     *
     * <p>204 rather than 404: "no link yet" is the ordinary state of most
     * achievements, and the sharing panel needs to render that state without
     * treating it as an error. A 404 here would make every un-shared achievement
     * look like a bug in the browser console.
     */
    @GetMapping("/{id}/share")
    public ResponseEntity<ShareLinkResponse> getShareLink(
            Authentication authentication,
            @PathVariable("id") Long id) {

        User currentUser = getAuthenticatedUser(authentication);
        ShareLinkResponse response = shareService.getShareLink(id, currentUser.getId());

        return response == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/achievements/{id}/share — extend the expiry or change the
     * proof-document setting, keeping the same token.
     *
     * <p>The token deliberately survives, so a link already sent to a reviewer
     * keeps working after the owner extends it. Rotating the token on every edit
     * would mean "extend by a day" silently broke the link you were extending.
     */
    @PatchMapping("/{id}/share")
    public ResponseEntity<ShareLinkResponse> updateShareLink(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody ShareLinkCreateRequest request) {

        User currentUser = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(shareService.updateShareLink(id, currentUser.getId(), request));
    }

    /**
     * DELETE /api/achievements/{id}/share — kill the link now.
     *
     * <p>Idempotent: revoking an already-dead link is a 204, not an error. The
     * caller wanted the link to stop working, and it does.
     */
    @DeleteMapping("/{id}/share")
    public ResponseEntity<Void> revokeShareLink(
            Authentication authentication,
            @PathVariable("id") Long id) {

        User currentUser = getAuthenticatedUser(authentication);
        shareService.revokeShareLink(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/achievements/shared — every link I have created, newest first.
     *
     * <p>Powers the "My Research &amp; Shared Resources" screen. The literal
     * {@code shared} segment beats {@code AchievementController}'s {@code /{id}}
     * pattern in Spring's path matching (a fixed word is more specific than a
     * placeholder), so this does not shadow or get shadowed by the achievement
     * lookup.
     *
     * <p>Scoped to the caller by construction — there is no user id to pass, so
     * there is nothing to tamper with.
     */
    @GetMapping("/shared")
    public ResponseEntity<List<ShareLinkResponse>> getMyShareLinks(Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(shareService.getMyShareLinks(currentUser.getId()));
    }
}
