package com.niet.facultyachievement.config;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.PublicSlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gives the users who existed before Track B a public profile address, once.
 *
 * <p>Migration V4 adds {@code users.public_slug} as a nullable column and leaves it
 * empty on purpose. Building a slug means stripping accents, lower-casing,
 * collapsing punctuation, guarding against a name that reduces to nothing, and
 * resolving collisions with a numeric suffix. All of that is ordinary, readable,
 * testable code in {@link PublicSlugGenerator}; in a MySQL statement it would be a
 * wall of nested {@code REPLACE()} calls that nobody could safely change later.
 *
 * <p><strong>Idempotent, like {@link AdminBootstrap}.</strong> It only looks at rows
 * where {@code public_slug IS NULL}, so a restart does nothing and — importantly —
 * an existing slug is never rewritten. Once a profile address has been published,
 * changing it would break every link anyone saved.
 *
 * <p>Runs after {@code AdminBootstrap} ({@code @Order(1)} there, {@code @Order(2)}
 * here) so a freshly created administrator is included in the same pass. An admin
 * never appears in the public directory anyway — {@code findPublicProfileBySlug}
 * excludes them — but leaving one row unfilled would be a puzzle for the next person
 * reading the table.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class PublicSlugBackfill implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PublicSlugGenerator publicSlugGenerator;

    @Override
    public void run(String... args) {
        List<User> withoutSlug = userRepository.findByPublicSlugIsNull();

        if (withoutSlug.isEmpty()) {
            log.debug("Public slug backfill: nothing to do, every user already has a slug.");
            return;
        }

        // Slugs handed out during this run are remembered here as well as checked in
        // the database. Two people called "Amit Kumar" in CSE would otherwise both be
        // given the same slug in the same pass: neither is in the table yet, so the
        // database check clears both, and the second save fails on the unique key.
        Set<String> assignedInThisRun = new HashSet<>();

        for (User user : withoutSlug) {
            String slug = publicSlugGenerator.generateFor(user, assignedInThisRun);
            user.setPublicSlug(slug);
            userRepository.save(user);
            assignedInThisRun.add(slug);
        }

        log.info("Public slug backfill: assigned {} public profile slug(s).", assignedInThisRun.size());
    }
}
