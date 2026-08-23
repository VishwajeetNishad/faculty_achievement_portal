package com.niet.facultyachievement.dto;

import lombok.*;

import java.util.List;

/**
 * The permissions currently held by one user, plus just enough identity
 * information for the Admin screen to show who is being edited.
 *
 * <p>Deliberately does NOT contain passwordHash, phone, or anything else not
 * needed to render the permission editor.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermissionsResponse {

    private Long userId;
    private String employeeId;
    private String fullName;
    private String email;
    private String role;
    private String departmentCode;
    private String departmentName;

    /** The permission codes this user effectively holds. */
    private List<String> permissionCodes;

    /**
     * True when the codes above come from the user's role rather than from
     * individual grants — i.e. the user is an administrator, who implicitly
     * holds everything. The UI uses this to explain why the checkboxes are
     * all ticked and read-only.
     */
    private boolean allFromRole;
}
