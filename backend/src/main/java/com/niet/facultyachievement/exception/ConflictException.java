package com.niet.facultyachievement.exception;

/**
 * Thrown when a request is well-formed and the caller is allowed to make it,
 * but carrying it out would leave the system in an invalid state.
 *
 * <p>Maps to HTTP 409 Conflict. Used for rules such as "the system must always
 * keep at least one active administrator" and "a department that still has
 * users cannot be deleted" — these are not the caller's mistake (400) and not
 * a permission problem (403), they are a clash with the system's current state.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
