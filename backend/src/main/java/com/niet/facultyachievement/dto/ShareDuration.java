package com.niet.facultyachievement.dto;

import java.time.Duration;

/**
 * How long a share link should last.
 *
 * <p>A fixed menu rather than a free-form number of minutes. Two reasons: the
 * design asks for a dropdown, and a closed list means the server never has to
 * decide whether "525600" is a reasonable request. {@link #CUSTOM} is the escape
 * hatch, and the one option where the server validates the value it is handed.
 *
 * <p>This enum is never stored. Only the resulting timestamp is persisted, so
 * adding or removing an option later needs no migration and cannot orphan
 * existing links.
 */
public enum ShareDuration {

    THIRTY_MINUTES(Duration.ofMinutes(30)),
    ONE_HOUR(Duration.ofHours(1)),
    SIX_HOURS(Duration.ofHours(6)),
    TWELVE_HOURS(Duration.ofHours(12)),
    TWENTY_FOUR_HOURS(Duration.ofHours(24)),
    SEVEN_DAYS(Duration.ofDays(7)),

    /**
     * No expiry at all.
     *
     * <p>Worth being blunt about what this is: a standing bearer credential for
     * possibly unpublished research, valid until somebody remembers to revoke
     * it. It is built because the feature was asked for; the UI warns about it,
     * and revoking is one click.
     */
    PERMANENT(null),

    /** Expiry supplied by the caller and validated by the server. */
    CUSTOM(null);

    private final Duration duration;

    ShareDuration(Duration duration) {
        this.duration = duration;
    }

    /** The fixed length of this option, or {@code null} for PERMANENT and CUSTOM. */
    public Duration getDuration() {
        return duration;
    }
}
