package com.niet.facultyachievement.exception;

/**
 * Thrown when a client exceeds the allowed number of failed login attempts.
 * Handled by {@code GlobalExceptionHandler} as HTTP 429 (Too Many Requests)
 * with a {@code Retry-After} header.
 */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
