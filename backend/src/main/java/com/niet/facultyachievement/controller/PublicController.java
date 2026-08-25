package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.DepartmentResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.dto.publicview.PublicAchievementResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyProfileResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyResponse;
import com.niet.facultyachievement.dto.publicview.SharedAchievementResponse;
import com.niet.facultyachievement.service.PublicDiscoveryService;
import com.niet.facultyachievement.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The only part of this application a stranger can reach.
 *
 * <p>Everything under {@code /api/public} is whitelisted in {@code SecurityConfig}
 * and runs with <strong>no authentication at all</strong>. There is no
 * {@code Authentication} parameter anywhere in this class, and that absence is the
 * point: no method here can be tricked into behaving as though somebody were
 * logged in, because none of them has a caller to impersonate.
 *
 * <p>Three rules make that safe:
 *
 * <ol>
 *   <li><strong>Dedicated response classes.</strong> Everything returned comes from
 *       {@code dto.publicview} (plus {@code DepartmentResponse}, which is only a
 *       code and a name). The authenticated {@code AchievementResponse} — which
 *       carries reviewer comments, the faculty member's email, their employee id
 *       and the proof document URL — is never used here. It physically cannot
 *       leak, because it is never constructed on this path.</li>
 *   <li><strong>The visibility rule is not a parameter.</strong> No endpoint accepts
 *       {@code status} or {@code visibility}. {@code APPROVED} + {@code PUBLIC} is
 *       written as a literal inside every repository query, so there is no value a
 *       visitor could send that would widen what they see.</li>
 *   <li><strong>Share tokens are re-checked every time.</strong> Expiry and
 *       revocation are decided by the server on each request. The countdown in the
 *       browser is decoration.</li>
 * </ol>
 *
 * <p>Note also what is <em>not</em> here: no login, no signup, no password reset,
 * no counts of pending or rejected work, and no way to reach a proof document
 * except through a token whose owner explicitly opted in.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicDiscoveryService publicDiscoveryService;
    private final ShareService shareService;

    /* ── Directory & profiles ─────────────────────────────────────────── */

    /**
     * GET /api/public/faculty — the public faculty directory.
     *
     * <p>Only lists people who are active <em>and</em> have at least one publicly
     * visible achievement. Someone with nothing published is not in this list, so
     * the directory never doubles as a complete staff roster.
     *
     * <p>{@code size} is clamped in the service. An endpoint with no authentication
     * in front of it should not let a stranger ask for a hundred thousand rows in
     * one call.
     */
    @GetMapping("/faculty")
    public ResponseEntity<PagedResponse<PublicFacultyResponse>> searchFaculty(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "departmentCode", required = false) String departmentCode,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {

        return ResponseEntity.ok(
                publicDiscoveryService.searchFaculty(keyword, departmentCode, page, size));
    }

    /**
     * GET /api/public/faculty/{slug} — one person's public profile header.
     *
     * <p>The slug is a readable address like {@code rajesh-kumar-cse} rather than a
     * database id, so the URL means something to a human and reveals nothing about
     * how many users exist or in what order they were created.
     *
     * <p>Unknown slug, inactive account and "nothing published" all return the same
     * 404. Telling them apart would confirm which accounts exist.
     */
    @GetMapping("/faculty/{slug}")
    public ResponseEntity<PublicFacultyProfileResponse> getFacultyProfile(
            @PathVariable("slug") String slug) {

        return ResponseEntity.ok(publicDiscoveryService.getFacultyProfile(slug));
    }

    /**
     * GET /api/public/faculty/{slug}/achievements — that person's public work.
     *
     * <p>{@code categoryCode} narrows by type ({@code PUBLICATION}, {@code PATENT}
     * and so on). It cannot widen anything: an unknown code simply matches nothing.
     */
    @GetMapping("/faculty/{slug}/achievements")
    public ResponseEntity<PagedResponse<PublicAchievementResponse>> getFacultyAchievements(
            @PathVariable("slug") String slug,
            @RequestParam(value = "categoryCode", required = false) String categoryCode,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        return ResponseEntity.ok(
                publicDiscoveryService.getFacultyAchievements(slug, categoryCode, page, size));
    }

    /**
     * GET /api/public/achievements — the public research gallery, across everyone.
     *
     * <p>This is what the home page's featured strip and the public achievements
     * page both read. The keyword is matched against the title, the author's own
     * keywords, the description, the faculty member's name and the journal or
     * conference name — the things a student actually types.
     */
    @GetMapping("/achievements")
    public ResponseEntity<PagedResponse<PublicAchievementResponse>> searchAchievements(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryCode", required = false) String categoryCode,
            @RequestParam(value = "departmentCode", required = false) String departmentCode,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {

        return ResponseEntity.ok(publicDiscoveryService.searchAchievements(
                keyword, categoryCode, departmentCode, page, size));
    }

    /**
     * GET /api/public/departments — for the filter dropdown.
     *
     * <p>Reuses the existing {@code DepartmentResponse}, which is safe to reuse
     * because a department is a code, a name and a description — nothing about it
     * is confidential. The service calls the single-argument factory so the
     * {@code userCount} field stays null and is omitted from the JSON: how many
     * staff a department has is internal information, not part of a filter list.
     */
    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentResponse>> getDepartments() {
        return ResponseEntity.ok(publicDiscoveryService.getDepartments());
    }

    /* ── Share links ──────────────────────────────────────────────────── */

    /**
     * GET /api/public/share/{token} — open a shared achievement.
     *
     * <p>The token in the URL is the entire credential. Outcomes:
     * 200 for a live link, 404 for a token that was never issued,
     * 410 with {@code "reason":"EXPIRED"} or {@code "reason":"REVOKED"} for one that
     * has stopped working. The 404/410 split is useful — one says "check for a
     * typo", the other says "ask for a fresh link" — and is safe here only because
     * the tokens are 32 random bytes and cannot be stumbled upon.
     *
     * <p>{@code Cache-Control: no-store} matters more than it looks. Without it a
     * shared browser or an intermediate proxy could keep unpublished research on
     * disk after the link had expired, quietly outliving the expiry the owner set.
     */
    @GetMapping("/share/{token}")
    public ResponseEntity<SharedAchievementResponse> getSharedAchievement(
            @PathVariable("token") String token) {

        SharedAchievementResponse response = shareService.getSharedAchievement(token);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    /**
     * GET /api/public/share/{token}/document — the proof PDF, if it was shared.
     *
     * <p>Reachable only when the owner ticked "include proof document"; otherwise
     * 403. The token is re-validated from scratch here, independently of the page
     * load — a link can expire between the visitor opening the page and clicking
     * download, and this request is the one that has to notice.
     *
     * <p>The file is streamed through the existing storage layer, so the uploads
     * directory is never exposed and the path-traversal guard still applies. The
     * filename offered is generic on purpose: the stored name is a UUID and there
     * is no reason to tell the visitor what it is.
     */
    @GetMapping("/share/{token}/document")
    public ResponseEntity<Resource> getSharedProofDocument(@PathVariable("token") String token) {
        Resource fileResource = shareService.getSharedProofDocument(token);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"shared_research_document.pdf\"")
                .body(fileResource);
    }
}
