package com.niet.facultyachievement.security;

import com.niet.facultyachievement.dto.ShareDuration;
import com.niet.facultyachievement.dto.ShareLinkCreateRequest;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.ShareLink;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.GoneException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.ShareLinkRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.AuditLogService;
import com.niet.facultyachievement.service.FileStorageService;
import com.niet.facultyachievement.service.ShareServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Track B — the rules that make a share link safe to hand to a stranger.
 *
 * <p>Pure Mockito, matching {@code AchievementServiceTest}. Every property here
 * is enforced in {@link ShareServiceImpl}, so it is provable without a database:
 *
 * <ul>
 *   <li>only the owner may create or revoke a link (403 otherwise);</li>
 *   <li>a token is 32 bytes of {@code SecureRandom} — unique, high-entropy, and
 *       never derived from an id, employee id or timestamp;</li>
 *   <li>expiry and revocation are decided server-side on every request
 *       (404 unknown, 410 REVOKED, 410 EXPIRED);</li>
 *   <li>the proof document is unreachable unless the owner opted in;</li>
 *   <li>the token itself never reaches the audit trail.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Track B — share-link ownership, token secrecy and lifecycle")
class ShareLinkSecurityTest {

    @Mock private ShareLinkRepository shareLinkRepository;
    @Mock private AchievementRepository achievementRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ShareServiceImpl shareService;

    private User owner;               // id 1
    private Achievement achievement;  // id 100, owned by owner

    /** 32 bytes, Base64-url without padding, is exactly 43 characters. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).employeeId("EMP-1").fullName("Dr. Owner")
                .email("owner@niet.co.in").build();

        achievement = Achievement.builder().id(100L).user(owner)
                .title("Federated Learning at the Edge").build();
    }

    private ShareLinkCreateRequest permanentRequest() {
        return ShareLinkCreateRequest.builder()
                .duration(ShareDuration.PERMANENT)
                .includeProofDocument(false)
                .build();
    }

    // ------------------------------------------------- Ownership (B21 test 13)

    @Test
    @DisplayName("A non-owner cannot create a share link for someone else's achievement (403)")
    void nonOwnerCannotCreate() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(achievement));

        assertThrows(AccessDeniedException.class, () ->
                shareService.createShareLink(100L, 999L, permanentRequest()));

        verify(shareLinkRepository, never()).save(any(ShareLink.class));
    }

    @Test
    @DisplayName("A non-owner cannot revoke someone else's share link (403)")
    void nonOwnerCannotRevoke() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(achievement));

        assertThrows(AccessDeniedException.class, () ->
                shareService.revokeShareLink(100L, 999L));
    }

    // ----------------------------------------------- Token lifecycle (B9, B19)

    @Test
    @DisplayName("An unknown token is a 404, and the error never echoes the token back")
    void unknownTokenIsNotFound() {
        when(shareLinkRepository.findByTokenWithDetails("no-such-token")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                shareService.getSharedAchievement("no-such-token"));

        assertFalse(ex.getMessage().contains("no-such-token"),
                "A not-found message must never repeat the token");
    }

    @Test
    @DisplayName("A blank token is refused without ever touching the database")
    void blankTokenIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                shareService.getSharedAchievement("   "));

        verifyNoInteractions(shareLinkRepository);
    }

    @Test
    @DisplayName("A revoked link returns 410 with reason REVOKED")
    void revokedLinkIsGone() {
        ShareLink link = ShareLink.builder().id(5L).achievement(achievement).createdBy(owner)
                .shareToken("tok-revoked").revoked(true).build();
        when(shareLinkRepository.findByTokenWithDetails("tok-revoked")).thenReturn(Optional.of(link));

        GoneException ex = assertThrows(GoneException.class, () ->
                shareService.getSharedAchievement("tok-revoked"));

        assertEquals("REVOKED", ex.getReason());
    }

    @Test
    @DisplayName("An expired link returns 410 with reason EXPIRED (re-judged server-side)")
    void expiredLinkIsGone() {
        ShareLink link = ShareLink.builder().id(6L).achievement(achievement).createdBy(owner)
                .shareToken("tok-expired").revoked(false)
                .expiresAt(LocalDateTime.now().minusHours(1)).build();
        when(shareLinkRepository.findByTokenWithDetails("tok-expired")).thenReturn(Optional.of(link));
        when(shareLinkRepository.markExpiryObserved(eq(6L), any(LocalDateTime.class))).thenReturn(1);

        GoneException ex = assertThrows(GoneException.class, () ->
                shareService.getSharedAchievement("tok-expired"));

        assertEquals("EXPIRED", ex.getReason());
    }

    @Test
    @DisplayName("The proof document is unreachable unless the owner included it")
    void proofDocumentBlockedWhenNotIncluded() {
        ShareLink link = ShareLink.builder().id(7L).achievement(achievement).createdBy(owner)
                .shareToken("tok-live").revoked(false)
                .includeProofDocument(false).build();
        when(shareLinkRepository.findByTokenWithDetails("tok-live")).thenReturn(Optional.of(link));

        assertThrows(AccessDeniedException.class, () ->
                shareService.getSharedProofDocument("tok-live"));

        verifyNoInteractions(fileStorageService);
    }

    // --------------------------------------------------- Token entropy (B8)

    @Test
    @DisplayName("1,000 tokens from identical input are all distinct, 43-char URL-safe base64")
    void tokensAreUniqueAndHighEntropy() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(achievement));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(shareLinkRepository.existsByShareToken(anyString())).thenReturn(false);
        when(shareLinkRepository.revokeAllForAchievement(anyLong(), any(LocalDateTime.class))).thenReturn(0);
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int n = 1000;
        // Identical inputs every time: same achievement, same owner, same options.
        // If the token were derived from any of those (or from a timestamp), the
        // outputs would collide. They must not.
        for (int i = 0; i < n; i++) {
            shareService.createShareLink(100L, 1L, permanentRequest());
        }

        ArgumentCaptor<ShareLink> captor = ArgumentCaptor.forClass(ShareLink.class);
        verify(shareLinkRepository, times(n)).save(captor.capture());

        Set<String> tokens = new HashSet<>();
        for (ShareLink saved : captor.getAllValues()) {
            String token = saved.getShareToken();
            assertNotNull(token, "A share link must always carry a token");
            assertTrue(TOKEN_PATTERN.matcher(token).matches(),
                    "Token must be 43-char URL-safe base64 (32 bytes of entropy): " + token);
            tokens.add(token);
        }
        assertEquals(n, tokens.size(),
                "All " + n + " tokens must be unique — a collision means low entropy");
    }

    // --------------------------------------- Token never leaks into the audit

    @Test
    @DisplayName("The share token is never written into an audit-log description")
    void tokenNeverAppearsInAudit() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(achievement));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(shareLinkRepository.existsByShareToken(anyString())).thenReturn(false);
        when(shareLinkRepository.revokeAllForAchievement(anyLong(), any(LocalDateTime.class))).thenReturn(0);
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shareService.createShareLink(100L, 1L, permanentRequest());

        ArgumentCaptor<ShareLink> linkCaptor = ArgumentCaptor.forClass(ShareLink.class);
        verify(shareLinkRepository).save(linkCaptor.capture());
        String token = linkCaptor.getValue().getShareToken();

        // Capture every audit description written during the create and prove the
        // secret is in none of them. The 5th argument being a User selects the
        // logAction(..., User, String) overload.
        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, atLeastOnce()).logAction(
                any(), anyString(), any(), descriptionCaptor.capture(), any(User.class), any());

        for (String description : descriptionCaptor.getAllValues()) {
            assertFalse(description.contains(token),
                    "An audit description must never contain the share token");
        }
    }
}
