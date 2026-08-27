package com.niet.facultyachievement.security;

import com.niet.facultyachievement.dto.HighlightMetadataRequest;
import com.niet.facultyachievement.dto.HighlightOrderRequest;
import com.niet.facultyachievement.dto.HighlightResponse;
import com.niet.facultyachievement.dto.publicview.PublicHighlightResponse;
import com.niet.facultyachievement.controller.HighlightController;
import com.niet.facultyachievement.controller.PublicController;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.HighlightFocalPoint;
import com.niet.facultyachievement.entity.HomepageHighlight;
import com.niet.facultyachievement.entity.Role;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.HomepageHighlightRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.AuditLogService;
import com.niet.facultyachievement.service.HighlightImageStorageService;
import com.niet.facultyachievement.service.HighlightService;
import com.niet.facultyachievement.service.HighlightServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The security contract of the homepage-highlights feature.
 *
 * <p><strong>Why this test is structural rather than an HTTP round-trip.</strong>
 * The approved plan asked for real status codes — anonymous {@code POST
 * /api/highlights} is rejected, a {@code ROLE_FACULTY} token gets 403, a token
 * holding {@code MANAGE_HIGHLIGHTS} succeeds. Asserting that needs a live filter
 * chain, which means {@code @SpringBootTest}; and with Flyway plus
 * {@code ddl-auto=validate} that would make {@code mvn test} require a running
 * MySQL. No test in this suite currently boots a Spring context — they are all
 * Mockito or reflection — and quietly turning {@code mvn test} into
 * "{@code mvn test}, but first start the database" is too high a price.
 *
 * <p>So the same guarantees are proved two other ways:
 *
 * <ul>
 *   <li><strong>Structurally</strong> — every mapped method on
 *       {@link HighlightController} carries a {@code @PreAuthorize} naming
 *       {@code MANAGE_HIGHLIGHTS}, every mutating method takes its actor from an
 *       injected {@link Authentication}, no request DTO offers a field an
 *       attacker could use to name a different actor, and the public DTO exposes
 *       nothing but the seven fields the carousel needs. A missing annotation is
 *       caught here, at compile-and-test time, rather than in production.</li>
 *   <li><strong>Behaviourally</strong> — the service-layer rules that
 *       {@code @PreAuthorize} cannot express: a retired banner's image stops
 *       being downloadable, a {@code javascript:} link never reaches the
 *       homepage, and a stale reorder is refused instead of silently stranding
 *       rows.</li>
 * </ul>
 *
 * <p>What is deliberately <em>not</em> claimed: this file does not prove Spring
 * Security is wired up, only that the metadata it reads is present and correct.
 * {@code SecurityConfig}'s {@code /api/public/**} permitAll and its
 * {@code anyRequest().authenticated()} catch-all are pre-existing and unchanged
 * by this feature.
 */
@ExtendWith(MockitoExtension.class)
class HighlightSecurityTest {

    private static final String ADMIN_EMAIL = "admin@niet.co.in";

    /** Fixed so the {@code ?v=} cache-busting token is predictable. */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 8, 27, 10, 30, 0);

    /** The six annotations that turn a method into an HTTP endpoint. All six are
     *  listed because {@code @GetMapping} does not report itself as a
     *  {@code @RequestMapping} through {@code isAnnotationPresent}. */
    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static final List<Class<? extends Annotation>> MUTATING_ANNOTATIONS = List.of(
            PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /** Substrings that would mean a request body is trying to name its own actor. */
    private static final List<String> ACTOR_SMELLS = List.of(
            "actor", "email", "userid", "uploadedby", "createdby", "adminid",
            "principal", "username", "password");

    @Mock
    private HomepageHighlightRepository highlightRepository;
    @Mock
    private HighlightImageStorageService imageStorage;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private HighlightServiceImpl highlightService;

    private User admin;

    @BeforeEach
    void setUp() {
        Role adminRole = Role.builder().id(3L).name("ROLE_ADMIN").build();
        Department department = Department.builder().id(1L).code("CSE")
                .name("Computer Science & Engineering").build();
        admin = User.builder().id(1L).employeeId("EMP-A1").fullName("Dr. Admin")
                .email(ADMIN_EMAIL).designation("Director").department(department)
                .role(adminRole).status(UserStatus.ACTIVE).build();
    }

    /* ================================================================
       Structural: the admin controller is closed by default
       ================================================================ */

    @Test
    @DisplayName("Every highlight endpoint is gated by MANAGE_HIGHLIGHTS or ROLE_ADMIN")
    void everyAdminEndpointIsGated() {
        List<Method> mapped = mappedMethods(HighlightController.class);

        // Guards against a vacuous pass: if the annotations were renamed or the
        // methods moved, an empty loop would otherwise "succeed".
        assertTrue(mapped.size() >= 6,
                "expected at least the six documented endpoints, found " + mapped.size());

        List<String> ungated = new ArrayList<>();
        for (Method method : mapped) {
            PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
            if (guard == null || !guard.value().contains(Permissions.MANAGE_HIGHLIGHTS)) {
                ungated.add(method.getName() + " -> "
                        + (guard == null ? "no @PreAuthorize" : guard.value()));
            }
        }

        assertTrue(ungated.isEmpty(),
                "highlight endpoints reachable without MANAGE_HIGHLIGHTS: " + ungated);
    }

    @Test
    @DisplayName("Every mutating highlight endpoint takes its actor from an injected Authentication")
    void mutatingEndpointsTakeTheActorFromTheSecurityContext() {
        List<String> missingActor = mappedMethods(HighlightController.class).stream()
                .filter(HighlightSecurityTest::isMutating)
                .filter(method -> Arrays.stream(method.getParameterTypes())
                        .noneMatch(Authentication.class::isAssignableFrom))
                .map(Method::getName)
                .toList();

        assertTrue(missingActor.isEmpty(),
                "mutating endpoints with no Authentication parameter, so the actor could only "
                        + "have come from the request: " + missingActor);
    }

    @Test
    @DisplayName("No highlight request body offers a field for naming the actor")
    void requestBodiesCannotNameTheirOwnActor() {
        assertNoActorFields(HighlightMetadataRequest.class);
        assertNoActorFields(HighlightOrderRequest.class);
    }

    /* ================================================================
       Structural: the public side leaks nothing
       ================================================================ */

    @Test
    @DisplayName("The public highlight payload is exactly the seven fields the carousel needs")
    void publicPayloadIsMinimal() {
        Set<String> expected = Set.of(
                "imagepath", "alttext", "caption", "linkurl", "focalpoint",
                "imagewidth", "imageheight");

        assertEquals(expected, instanceFieldNames(PublicHighlightResponse.class),
                "PublicHighlightResponse changed shape. Every field here is served to anonymous "
                        + "visitors, so anything added must be a deliberate decision, not a "
                        + "copy-paste from the admin DTO.");
    }

    @Test
    @DisplayName("Neither highlight DTO exposes the stored filename")
    void noDtoExposesTheStoredFilename() {
        // The stored name is a UUID inside the uploads directory. It is not a
        // secret, but publishing it invites requests that bypass the controller's
        // active check, so neither response carries it.
        for (Class<?> dto : List.of(PublicHighlightResponse.class, HighlightResponse.class)) {
            Set<String> fields = instanceFieldNames(dto);
            assertFalse(fields.contains("storedfilename"), dto.getSimpleName() + " leaks storedFilename");
            assertFalse(fields.contains("filename"), dto.getSimpleName() + " leaks filename");
        }
    }

    @Test
    @DisplayName("The public payload carries no uploader identity")
    void publicPayloadCarriesNoUploaderIdentity() {
        Set<String> fields = instanceFieldNames(PublicHighlightResponse.class);

        for (String smell : ACTOR_SMELLS) {
            assertFalse(fields.stream().anyMatch(name -> name.contains(smell)),
                    "PublicHighlightResponse exposes staff identity via a field containing '" + smell + "'");
        }
        // The admin table does show who uploaded a banner — that is intentional,
        // and it is why the two DTOs exist separately.
        assertTrue(instanceFieldNames(HighlightResponse.class).contains("uploadedbyname"),
                "the admin DTO should still name the uploader");
    }

    @Test
    @DisplayName("PublicController never receives an authenticated actor")
    void publicControllerNeverReceivesAnActor() {
        List<String> offenders = mappedMethods(PublicController.class).stream()
                .filter(method -> Arrays.stream(method.getParameterTypes())
                        .anyMatch(type -> Authentication.class.isAssignableFrom(type)
                                || Principal.class.isAssignableFrom(type)))
                .map(Method::getName)
                .toList();

        assertTrue(offenders.isEmpty(),
                "public endpoints must behave identically for every caller, but these can see who "
                        + "is asking: " + offenders);
    }

    /* ================================================================
       Structural: the permission itself
       ================================================================ */

    @Test
    @DisplayName("MANAGE_HIGHLIGHTS is in the catalogue and is delegable, not admin-only")
    void theNewPermissionIsRegisteredAndDelegable() {
        assertTrue(Permissions.ALL.contains(Permissions.MANAGE_HIGHLIGHTS),
                "MANAGE_HIGHLIGHTS is missing from Permissions.ALL, so PermissionCatalogValidator "
                        + "will log catalogue drift at startup");

        // It edits marketing content and cannot hand out further power, so it is
        // delegable to a communications person the way MANAGE_DEPARTMENTS is.
        // MANAGE_PERMISSIONS and CREATE_ADMIN stay locked to administrators.
        assertFalse(Permissions.ADMIN_ONLY_GRANTABLE.contains(Permissions.MANAGE_HIGHLIGHTS));
        assertTrue(Permissions.ADMIN_ONLY_GRANTABLE.contains(Permissions.MANAGE_PERMISSIONS));
        assertTrue(Permissions.ADMIN_ONLY_GRANTABLE.contains(Permissions.CREATE_ADMIN));
    }

    @Test
    @DisplayName("The permission catalogue holds 16 codes, matching the seeded rows")
    void theCatalogueSizeMatchesTheMigrations() {
        /* V1-V3 seed 15 permissions and V5 seeds the 16th. If this fails, a Java
           constant was added without a migration (or the reverse) and
           PermissionCatalogValidator will report drift on the next startup. Fix
           the pair, then update this number. */
        assertEquals(16, Permissions.ALL.size(), Permissions.ALL.toString());
    }

    /* ================================================================
       Behavioural: retiring a banner really does unpublish it
       ================================================================ */

    @Test
    @DisplayName("A retired banner's image stops being downloadable, and the file is never read")
    void aRetiredBannerIsNotDownloadable() {
        when(highlightRepository.findById(7L)).thenReturn(Optional.of(highlight(7L, false, 1)));

        ResourceNotFoundException ex =
                assertThrows(ResourceNotFoundException.class, () -> highlightService.loadImage(7L));

        assertEquals("Highlight image not found.", ex.getMessage());
        // Not merely unlisted: the bytes are never even fetched, so a URL someone
        // bookmarked or a search engine indexed stops working too.
        verify(imageStorage, never()).loadAsResource(anyString());
    }

    @Test
    @DisplayName("A retired banner and a banner that never existed give the identical message")
    void retiredAndMissingAreIndistinguishable() {
        when(highlightRepository.findById(99L)).thenReturn(Optional.empty());

        String missing = assertThrows(ResourceNotFoundException.class,
                () -> highlightService.loadImage(99L)).getMessage();

        // Different wording here would let an anonymous visitor enumerate which
        // ids exist, which is the whole reason both paths share one string.
        assertEquals("Highlight image not found.", missing);
    }

    @Test
    @DisplayName("A live banner streams its stored file with the detected content type")
    void aLiveBannerStreamsItsFile() {
        HomepageHighlight live = highlight(3L, true, 1);
        when(highlightRepository.findById(3L)).thenReturn(Optional.of(live));
        when(imageStorage.loadAsResource("3.png")).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        HighlightService.HighlightImage image = highlightService.loadImage(3L);

        assertNotNull(image.resource());
        assertEquals("image/png", image.contentType());
        assertEquals(2048L, image.sizeBytes());
    }

    @Test
    @DisplayName("The public list serves a version-stamped path built from updated_at")
    void thePublicListIsVersionStamped() {
        when(highlightRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(highlight(3L, true, 1)));

        List<PublicHighlightResponse> published = highlightService.listPublic();

        assertEquals(1, published.size());
        // The ?v= token is what makes the year-long immutable cache safe: replacing
        // an image bumps updated_at, which changes the URL, which forces a refetch.
        assertEquals("/public/highlights/3/image?v=" + FIXED_TIME.toEpochSecond(ZoneOffset.UTC),
                published.get(0).getImagePath());
    }

    /* ================================================================
       Behavioural: an admin cannot put a script URL on the homepage
       ================================================================ */

    @Test
    @DisplayName("A javascript: link is refused before any file is written")
    void javascriptLinksAreRefusedBeforeStoringAnything() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        HighlightMetadataRequest request = HighlightMetadataRequest.builder()
                .altText("Award ceremony")
                .linkUrl("javascript:alert(document.cookie)")
                .build();

        assertThrows(BadRequestException.class,
                () -> highlightService.create(anyImage(), request, ADMIN_EMAIL));

        /* Validation runs before storage on purpose. If it ran after, every
           rejected attempt would still leave a file on disk that nothing points
           at — a slow disk-fill reachable by anyone holding MANAGE_HIGHLIGHTS. */
        verify(imageStorage, never()).store(any());
        verify(highlightRepository, never()).saveAndFlush(any(HomepageHighlight.class));
    }

    @Test
    @DisplayName("A protocol-relative //host link is refused — it leaves the site")
    void protocolRelativeLinksAreRefused() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        HighlightMetadataRequest request = HighlightMetadataRequest.builder()
                .altText("Award ceremony")
                // Looks like a same-site path and is not: browsers read this as
                // https://evil.example, so a bare "starts with /" check is not enough.
                .linkUrl("//evil.example/phish")
                .build();

        assertThrows(BadRequestException.class,
                () -> highlightService.create(anyImage(), request, ADMIN_EMAIL));

        verify(imageStorage, never()).store(any());
    }

    @Test
    @DisplayName("A valid upload lands at the end of the carousel and goes live by default")
    void aValidUploadLandsAtTheEnd() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(imageStorage.store(any())).thenReturn(new HighlightImageStorageService.StoredImage(
                "stored-uuid.png", "image/png", 51_200L, 1600, 730));
        when(highlightRepository.findMaxDisplayOrder()).thenReturn(4);
        when(highlightRepository.saveAndFlush(any(HomepageHighlight.class))).thenAnswer(call -> {
            HomepageHighlight saving = call.getArgument(0);
            saving.setId(9L);
            saving.setUpdatedAt(FIXED_TIME);
            return saving;
        });

        HighlightMetadataRequest request = HighlightMetadataRequest.builder()
                .altText("  Dr. Neema Agarwal at SIIEJ 2026, Tokyo  ")
                .linkUrl("https://www.niet.co.in/")
                .build();

        HighlightResponse created = highlightService.create(anyImage(), request, ADMIN_EMAIL);

        assertEquals(5, created.getDisplayOrder(), "should sit after the four existing slides");
        assertEquals(Boolean.TRUE, created.getActive());
        assertEquals(HighlightFocalPoint.CENTER, created.getFocalPoint(), "the safe default crop");
        assertEquals("Dr. Neema Agarwal at SIIEJ 2026, Tokyo", created.getAltText(), "alt text is trimmed");
        assertEquals("Dr. Admin", created.getUploadedByName());

        verify(auditLogService).logAction(eq(AuditAction.HIGHLIGHT_CREATED), eq("HOMEPAGE_HIGHLIGHT"),
                eq(9L), anyString(), eq(admin), isNull());
    }

    /* ================================================================
       Behavioural: reordering is all-or-nothing
       ================================================================ */

    @Test
    @DisplayName("A reorder that omits a slide is refused, and nothing is saved")
    void reorderRefusesAPartialList() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(highlightRepository.findAllWithUploader()).thenReturn(List.of(
                highlight(1L, true, 1), highlight(2L, true, 2), highlight(3L, true, 3)));

        // Two ids for three slides: an admin page that went stale. Accepting it
        // would strand slide 3 and silently reshuffle the homepage.
        assertThrows(BadRequestException.class,
                () -> highlightService.reorder(List.of(1L, 2L), ADMIN_EMAIL));

        verify(highlightRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("A reorder naming the same slide twice is refused")
    void reorderRefusesDuplicates() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class,
                () -> highlightService.reorder(List.of(1L, 1L, 2L), ADMIN_EMAIL));

        verify(highlightRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("An empty reorder is refused rather than treated as 'clear the carousel'")
    void reorderRefusesAnEmptyList() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class, () -> highlightService.reorder(List.of(), ADMIN_EMAIL));

        verify(highlightRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("A complete reorder assigns 1, 2, 3 in the submitted sequence")
    void reorderAssignsSequentialPositions() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(highlightRepository.findAllWithUploader()).thenReturn(List.of(
                highlight(1L, true, 1), highlight(2L, true, 2), highlight(3L, true, 3)));
        when(highlightRepository.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));

        List<HighlightResponse> reordered =
                highlightService.reorder(List.of(3L, 1L, 2L), ADMIN_EMAIL);

        Map<Long, Integer> positions = reordered.stream().collect(
                Collectors.toMap(HighlightResponse::getId, HighlightResponse::getDisplayOrder));

        assertEquals(Map.of(3L, 1, 1L, 2, 2L, 3), positions);
        // Never 0: display_order starts at 1 so "unset" and "first" cannot be
        // confused when a row is inserted by hand.
        assertFalse(positions.containsValue(0));
    }

    /* ================================================================
       Behavioural: editing text cannot republish a retired banner
       ================================================================ */

    @Test
    @DisplayName("Editing the caption of a retired banner leaves it retired")
    void editingTextCannotRepublishARetiredBanner() {
        HomepageHighlight retired = highlight(7L, false, 1);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(highlightRepository.findByIdWithUploader(7L)).thenReturn(Optional.of(retired));
        when(highlightRepository.saveAndFlush(any(HomepageHighlight.class)))
                .thenAnswer(call -> call.getArgument(0));

        // active is null: "leave it alone", not "make it true".
        HighlightMetadataRequest request = HighlightMetadataRequest.builder()
                .altText("Corrected alt text")
                .build();

        HighlightResponse updated = highlightService.updateMetadata(7L, request, ADMIN_EMAIL);

        assertEquals(Boolean.FALSE, updated.getActive(),
                "a text edit republished a banner somebody deliberately retired");
        assertEquals("Corrected alt text", updated.getAltText());
    }

    /* ================================================================
       Helpers
       ================================================================ */

    private static List<Method> mappedMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent))
                .toList();
    }

    private static boolean isMutating(Method method) {
        return MUTATING_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
    }

    /** Declared, non-static, non-synthetic field names, lower-cased. Synthetic
     *  fields are skipped because coverage tooling adds its own. */
    private static Set<String> instanceFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void assertNoActorFields(Class<?> requestDto) {
        Set<String> fields = instanceFieldNames(requestDto);
        for (String smell : ACTOR_SMELLS) {
            assertFalse(fields.stream().anyMatch(name -> name.contains(smell)),
                    requestDto.getSimpleName() + " has a field containing '" + smell
                            + "'. The actor must come from SecurityContextHolder, never the body.");
        }
    }

    /** A stand-in upload. The storage service is mocked in these tests, so the
     *  bytes are never inspected — {@code HighlightImageStorageTest} covers that. */
    private static MultipartFile anyImage() {
        return new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1, 2, 3, 4});
    }

    private HomepageHighlight highlight(Long id, boolean active, int displayOrder) {
        return HomepageHighlight.builder()
                .id(id)
                .storedFilename(id + ".png")
                .contentType("image/png")
                .fileSizeBytes(2048L)
                .imageWidth(1600)
                .imageHeight(730)
                .altText("Highlight " + id)
                .focalPoint(HighlightFocalPoint.CENTER)
                .displayOrder(displayOrder)
                .active(active)
                .uploadedBy(admin)
                .createdAt(FIXED_TIME)
                .updatedAt(FIXED_TIME)
                .build();
    }
}
