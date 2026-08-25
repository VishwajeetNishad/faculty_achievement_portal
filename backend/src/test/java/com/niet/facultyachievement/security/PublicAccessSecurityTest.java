package com.niet.facultyachievement.security;

import com.niet.facultyachievement.dto.publicview.PublicAchievementResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyProfileResponse;
import com.niet.facultyachievement.dto.publicview.PublicFacultyResponse;
import com.niet.facultyachievement.dto.publicview.SharedAchievementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track B — the public DTOs cannot leak sensitive fields, proven structurally.
 *
 * <p>The security control behind the public site is not a runtime filter that
 * could be forgotten; it is the shape of the response objects themselves. A
 * field that does not exist on the serialised class can never appear in the
 * JSON, however the service is later changed. These tests assert that shape by
 * reflection, so that the day someone adds {@code facultyEmail} to a public DTO,
 * a test — not a student — is the one that finds it.
 *
 * <p>Implements B21 tests 16–17: {@code verificationComment} and
 * {@code facultyEmail} must appear on no public or shared response body.
 */
@DisplayName("Track B — public DTOs cannot carry sensitive fields (structural proof)")
class PublicAccessSecurityTest {

    /**
     * Exact field names (case-insensitive) that must never appear on a response
     * an anonymous visitor or a share-link holder can see.
     */
    private static final Set<String> FORBIDDEN_EXACT = Set.of(
            "email", "facultyemail", "useremail", "contactemail",
            "phone", "phonenumber", "mobile", "contactnumber",
            "employeeid",
            "password", "passwordhash", "plainpassword",
            "verificationcomment", "reviewercomment", "reviewercomments", "reviewernote",
            "proofdocumenturl", "proofurl", "proofdocumentpath", "documentpath",
            "verifiedby", "verifiedbyuserid", "verifiedbyname", "verifiedat",
            "userid", "createdbyuserid", "internalnote"
    );

    /**
     * Substrings that must not appear anywhere in a field name — the high-signal
     * secrets that have no legitimate spelling on a public DTO, so a future
     * {@code reviewerComments} or {@code facultyEmailAddress} is caught too.
     *
     * <p>Deliberately does NOT include bare {@code "proof"}: the allowed boolean
     * flag {@code proofDocumentAvailable} advertises that a document exists
     * without exposing any URL, filename or path, and must not trip this check.
     */
    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "password", "verificationcomment", "reviewercomment", "proofdocumenturl"
    );

    private static final Class<?>[] PUBLIC_DTOS = {
            PublicAchievementResponse.class,
            PublicFacultyResponse.class,
            PublicFacultyProfileResponse.class,
            SharedAchievementResponse.class
    };

    @Test
    @DisplayName("No public/share DTO — nor any nested detail class — declares a sensitive field")
    void publicDtosDeclareNoSensitiveFields() {
        List<String> violations = new ArrayList<>();

        for (Class<?> root : PUBLIC_DTOS) {
            // Breadth-first over the DTO and every static nested class it declares
            // (the per-category detail objects live as nested classes).
            Deque<Class<?>> toVisit = new ArrayDeque<>();
            toVisit.add(root);
            while (!toVisit.isEmpty()) {
                Class<?> current = toVisit.poll();
                for (Class<?> nested : current.getDeclaredClasses()) {
                    toVisit.add(nested);
                }
                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic()) {
                        continue; // e.g. $jacocoData added by coverage instrumentation
                    }
                    String name = field.getName().toLowerCase(Locale.ROOT);

                    if (FORBIDDEN_EXACT.contains(name)) {
                        violations.add(current.getSimpleName() + "." + field.getName() + " (exact match)");
                    }
                    for (String forbidden : FORBIDDEN_SUBSTRINGS) {
                        if (name.contains(forbidden)) {
                            violations.add(current.getSimpleName() + "." + field.getName()
                                    + " (contains '" + forbidden + "')");
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Public DTOs must not expose sensitive fields, but found: " + violations);
    }

    @Test
    @DisplayName("Specifically: verificationComment and any email field are absent (B21 tests 16–17)")
    void namedSensitiveFieldsAreAbsent() {
        for (Class<?> dto : new Class<?>[]{PublicAchievementResponse.class, SharedAchievementResponse.class}) {
            List<String> fieldNames = new ArrayList<>();
            for (Field field : dto.getDeclaredFields()) {
                fieldNames.add(field.getName().toLowerCase(Locale.ROOT));
            }

            assertTrue(fieldNames.stream().noneMatch(n -> n.equals("verificationcomment")),
                    dto.getSimpleName() + " must not expose the reviewer's verificationComment");
            assertTrue(fieldNames.stream().noneMatch(n -> n.contains("email")),
                    dto.getSimpleName() + " must not expose any email field");
        }
    }
}
