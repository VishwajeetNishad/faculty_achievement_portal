package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Request body for {@code PUT /api/users/{userId}/permissions}.
 *
 * <p>The list is the COMPLETE set of permissions the user should end up with —
 * anything not listed is revoked. An empty list therefore removes every
 * permission, which is a valid and useful operation.
 *
 * <p>Note what is NOT here: no user id, no actor id, no role. The user being
 * edited comes from the URL, and the administrator performing the edit is
 * taken from the verified JWT via SecurityContextHolder. Identity is never
 * accepted from the request body, because a request body is fully controlled
 * by whoever sends it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionUpdateRequest {

    @NotNull(message = "permissionCodes is required (send an empty list to remove all permissions)")
    private List<String> permissionCodes;
}
