package com.niet.facultyachievement.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free, in-memory brute-force guard for the login endpoint.
 *
 * <p>It counts consecutive failed login attempts per (client IP + username) key.
 * After {@link #MAX_FAILED_ATTEMPTS} failures within {@link #ATTEMPT_WINDOW}, that
 * key is locked for {@link #LOCKOUT_DURATION}; a successful login clears the count.
 *
 * <p>Keying on IP + username means repeatedly guessing one account from one machine
 * gets locked out, without locking other users who share the same office/campus
 * (NAT) IP address. This intentionally does not try to stop large distributed
 * attacks — that belongs at the network/firewall layer. State is per-instance and
 * in memory, which is appropriate for the single-VM deployment.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    // Purge fully-expired entries every N recorded failures so the map, which is
    // keyed by attacker-controlled input, cannot grow without bound.
    private static final int CLEANUP_EVERY = 500;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final AtomicInteger sinceCleanup = new AtomicInteger();

    /** True if this key is currently locked out and the request should be rejected. */
    public boolean isBlocked(String ip, String username) {
        Attempt a = attempts.get(key(ip, username));
        return a != null && a.lockedUntilMillis > now();
    }

    /** Seconds left on the lockout (for the client message); 0 if not locked. */
    public long retryAfterSeconds(String ip, String username) {
        Attempt a = attempts.get(key(ip, username));
        if (a == null) {
            return 0;
        }
        long remaining = a.lockedUntilMillis - now();
        return remaining > 0 ? (remaining + 999) / 1000 : 0; // round up to whole seconds
    }

    /** Record a failed login; locks the key once the threshold is crossed. */
    public void recordFailure(String ip, String username) {
        long now = now();
        attempts.compute(key(ip, username), (k, a) -> {
            // Start a fresh window if there was none or the previous window elapsed.
            if (a == null || now - a.windowStartMillis > ATTEMPT_WINDOW.toMillis()) {
                a = new Attempt(now);
            }
            a.count++;
            if (a.count >= MAX_FAILED_ATTEMPTS) {
                a.lockedUntilMillis = now + LOCKOUT_DURATION.toMillis();
            }
            return a;
        });
        maybeCleanup(now);
    }

    /** Clear all recorded failures for a key after a successful login. */
    public void reset(String ip, String username) {
        attempts.remove(key(ip, username));
    }

    private void maybeCleanup(long now) {
        if (sinceCleanup.incrementAndGet() < CLEANUP_EVERY) {
            return;
        }
        sinceCleanup.set(0);
        // Remove only entries that are neither locked nor within an active window.
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            return a.lockedUntilMillis <= now
                    && now - a.windowStartMillis > ATTEMPT_WINDOW.toMillis();
        });
    }

    private static String key(String ip, String username) {
        String u = username == null ? "" : username.trim().toLowerCase();
        return (ip == null ? "" : ip) + "|" + u;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * Per-key counters. Updates happen inside ConcurrentHashMap.compute (atomic per
     * key); the fields read outside compute are volatile so readers see the latest
     * values.
     */
    private static final class Attempt {
        volatile int count;
        volatile long windowStartMillis;
        volatile long lockedUntilMillis;

        Attempt(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
