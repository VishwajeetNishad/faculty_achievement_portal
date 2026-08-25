package com.niet.facultyachievement.service;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the readable web address for a public faculty profile.
 *
 * <p>A public profile could have lived at {@code /faculty/17}, but a database id is
 * a poor thing to put in a URL: it is ugly to share, it announces how many accounts
 * exist and in what order they were created, and it invites a visitor to try
 * {@code /faculty/18} to see who else is in the system. A slug —
 * {@code rajesh-kumar-cse} — reads well, means something to a human, and gives away
 * nothing about the internal numbering.
 *
 * <p>This lives in one place because it has exactly two callers and they must never
 * disagree: {@code PublicSlugBackfill} fills in the users who existed before the
 * feature, and {@code UserManagementServiceImpl} assigns one to every new account.
 * Two copies of "how do we make a slug" would eventually drift, and the symptom
 * would be a profile URL that works for staff hired last year and not for staff
 * hired today.
 *
 * <p>Note what this class does <strong>not</strong> do: it never changes an existing
 * slug. Once a profile address has been published, changing it breaks every link
 * anyone saved, so correcting the spelling of a name deliberately does not move that
 * person's public URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PublicSlugGenerator {

    private final UserRepository userRepository;

    /** Anything that is not a lowercase letter or a digit becomes a separator. */
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");

    /** Accents are decomposed first, then these leftover marks are dropped. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    /** Matches the {@code public_slug} column width in migration V4. */
    private static final int MAX_SLUG_LENGTH = 120;

    /**
     * Room reserved for a {@code -123} collision suffix, so appending one can never
     * push the slug past the column width.
     */
    private static final int COLLISION_SUFFIX_ROOM = 5;

    /** Used when a name contains nothing a URL can carry. */
    private static final String FALLBACK_BASE = "faculty";

    /** Stops a runaway loop rather than imposing a real limit. */
    private static final int MAX_COLLISION_ATTEMPTS = 1000;

    /**
     * A slug for this user that nothing else is using.
     *
     * @param alreadyTaken slugs handed out earlier in the same batch but not yet
     *                     committed. Pass {@link Set#of()} when saving one user at a
     *                     time. This matters: two people called "Amit Kumar" in CSE
     *                     processed in one pass would both be handed the same slug,
     *                     because neither is in the table yet and the database check
     *                     would clear both — and the second save would then fail on
     *                     the unique constraint.
     */
    public String generateFor(User user, Set<String> alreadyTaken) {
        String base = baseSlugFor(user);

        String candidate = base;
        int suffix = 2;   // starts at 2 — the first person to get a name needs no "1"

        while (alreadyTaken.contains(candidate) || userRepository.existsByPublicSlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;

            if (suffix > MAX_COLLISION_ATTEMPTS) {
                // Reaching a thousand people whose names and department all collide
                // is not going to happen. Looping forever if some future change made
                // the uniqueness check always return true very much could, and a
                // startup that hangs is far harder to diagnose than one that logs.
                log.error("Could not find a free public slug for user id {} after {} attempts; "
                                + "falling back to an employee-id based slug.",
                        user.getId(), MAX_COLLISION_ATTEMPTS);
                return truncate(slugify(FALLBACK_BASE + "-" + user.getEmployeeId()));
            }
        }

        return candidate;
    }

    /** Convenience for the single-user case. */
    public String generateFor(User user) {
        return generateFor(user, Set.of());
    }

    /**
     * The name-and-department part, before any collision suffix.
     *
     * <p>The department code is included because it is genuinely useful: two
     * institutions' worth of faculty share very few full names, a name plus a
     * department is almost always unique, and it tells a reader something at a
     * glance.
     */
    private String baseSlugFor(User user) {
        String namePart = slugify(user.getFullName());

        if (namePart.isEmpty()) {
            // A name written entirely in a script this scheme cannot carry reduces
            // to nothing. Rather than produce an empty address, fall back to
            // something stable.
            namePart = slugify(FALLBACK_BASE + "-" + user.getEmployeeId());
            if (namePart.isEmpty()) {
                namePart = FALLBACK_BASE;
            }
        }

        String departmentPart = user.getDepartment() != null
                ? slugify(user.getDepartment().getCode())
                : "";

        String combined = departmentPart.isEmpty() ? namePart : namePart + "-" + departmentPart;
        return truncate(combined);
    }

    /**
     * Turn arbitrary text into the safe subset a URL can carry unescaped.
     *
     * <p>Accented letters are decomposed and their marks stripped, so "José" becomes
     * "jose" rather than "jos" or a percent-encoded mess. Everything else that is
     * not a lowercase letter or a digit becomes a single dash, and dashes at the ends
     * are trimmed.
     */
    private String slugify(String input) {
        if (input == null) {
            return "";
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_SLUG_CHARS.matcher(normalized).replaceAll("-");

        // Trim dashes by index rather than with a regex, so a string that is nothing
        // but punctuation ends up empty instead of as a single dash.
        int start = 0;
        int end = normalized.length();
        while (start < end && normalized.charAt(start) == '-') start++;
        while (end > start && normalized.charAt(end - 1) == '-') end--;

        return normalized.substring(start, end);
    }

    /** Keep the slug short enough that a collision suffix still fits the column. */
    private String truncate(String slug) {
        int limit = MAX_SLUG_LENGTH - COLLISION_SUFFIX_ROOM;
        if (slug.length() <= limit) {
            return slug;
        }
        String cut = slug.substring(0, limit);
        while (cut.endsWith("-")) {          // no dangling dash where the cut landed
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }
}
