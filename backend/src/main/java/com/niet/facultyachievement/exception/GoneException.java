package com.niet.facultyachievement.exception;

/**
 * The thing you asked for existed, and does not any more.
 *
 * <p>Maps to HTTP 410 Gone, and exists for exactly one situation: a share link
 * that has expired or been revoked.
 *
 * <p>Why not simply 404? Because the two answers mean different things to the
 * person holding the link, and telling them apart is genuinely useful. A 404
 * says "this address was never valid" — check for a typo. A 410 says "this was
 * valid and is now closed" — ask the sender for a fresh link. Collapsing both
 * into 404 would send people hunting for a transcription error that does not
 * exist.
 *
 * <p>Doing this leaks one bit of information: that some token once existed. That
 * is acceptable here, because the tokens are 32 random bytes. Nobody is going to
 * stumble onto a real one and learn anything from being told it expired. The
 * calculation would be entirely different for a guessable identifier, which is
 * precisely why the tokens are not guessable.
 *
 * <p>{@link #getReason()} carries a machine-readable {@code EXPIRED} or
 * {@code REVOKED} so the share page can show the right message without parsing
 * prose.
 */
public class GoneException extends RuntimeException {

    private final String reason;

    public GoneException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** {@code EXPIRED} or {@code REVOKED}. Never contains the token. */
    public String getReason() {
        return reason;
    }
}
