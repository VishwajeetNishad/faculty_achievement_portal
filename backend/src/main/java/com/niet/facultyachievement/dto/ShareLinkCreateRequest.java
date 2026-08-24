package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ask for a share link on one of your own achievements.
 *
 * <p>Note what is <strong>not</strong> here: no achievement id (it is in the
 * URL), and no owner or actor field of any kind. The person creating the link is
 * always taken from the security context. A body that could name its own owner
 * would be an invitation to create links on somebody else's work.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkCreateRequest {

    @NotNull(message = "Duration is required")
    private ShareDuration duration;

    /**
     * Required when {@link #duration} is {@code CUSTOM}, ignored otherwise. Must
     * be in the future — the service rejects a past date rather than quietly
     * creating a link that is already dead.
     */
    private LocalDateTime customExpiresAt;

    /**
     * Whether the recipient may also download the proof PDF.
     *
     * <p>Defaults to false because the field is a primitive: a body that omits
     * it gets the closed setting. Sharing a record is not the same as sharing the
     * evidence file behind it, and the safe default is the one you have to ask to
     * change.
     */
    private boolean includeProofDocument;
}
