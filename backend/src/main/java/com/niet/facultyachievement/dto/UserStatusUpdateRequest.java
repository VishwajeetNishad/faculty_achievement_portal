package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Activate, deactivate or suspend an account.
 *
 * <p>Its own endpoint and its own permission (MANAGE_USER_STATUS) because this
 * is the one user-management action that immediately cuts someone's access:
 * {@code CustomUserDetailsService} marks a non-ACTIVE account as disabled, and
 * the user is refused on their very next request even though their existing
 * token has not expired.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatusUpdateRequest {

    /** ACTIVE, INACTIVE or SUSPENDED. */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Optional free-text note recorded in the audit trail, so the log answers
     * "why was this person locked out?" and not only "when".
     */
    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;
}
