package com.niet.facultyachievement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    /**
     * A machine-readable cause, for the few errors where the page has to react
     * differently rather than just print the message.
     *
     * <p>Currently used only by the 410 on a dead share link, which sends
     * {@code EXPIRED} or {@code REVOKED} so the share page can show "this link
     * has expired, ask for a new one" or "this link was withdrawn" without
     * pattern-matching on English prose.
     *
     * <p>Annotated NON_NULL, so every existing error response is byte-for-byte
     * unchanged — the field simply is not there unless something sets it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reason;
}
