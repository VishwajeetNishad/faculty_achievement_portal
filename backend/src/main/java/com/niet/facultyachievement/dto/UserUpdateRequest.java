package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * What an administrator sends to edit an existing account.
 *
 * <p>Every field is optional: only the ones actually present are applied, so a
 * screen that edits just the phone number does not have to resend the whole
 * record and cannot accidentally blank out the rest.
 *
 * <p>Deliberately NOT here:
 * <ul>
 *   <li>{@code id} — the user being edited travels in the URL, so a mismatched
 *       body id can never redirect the change to a different account.</li>
 *   <li>{@code status} — changed through {@code PATCH /api/users/{id}/status},
 *       which is gated by its own MANAGE_USER_STATUS permission.</li>
 *   <li>{@code permissions} — changed through
 *       {@code PUT /api/users/{id}/permissions}, gated by MANAGE_PERMISSIONS.</li>
 * </ul>
 * Splitting these apart means an account can be given the ability to correct a
 * misspelled name without also gaining the ability to deactivate people or hand
 * out permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {

    @Size(max = 50, message = "Employee ID must not exceed 50 characters")
    private String employeeId;

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Email(message = "Enter a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;

    private Long departmentId;

    /**
     * Move the account to a different role. Treated as strictly as creating an
     * account in that role: promoting someone to Head of Department needs
     * CREATE_HOD, and promoting to Administrator needs CREATE_ADMIN.
     */
    private String role;

    /**
     * Optional password reset, for when a member of staff has lost theirs.
     * There is no "current password" field because an administrator does not
     * know it — that is the point of a reset. Hashed with BCrypt like any other
     * password, and never returned, logged or audited in plain text.
     */
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String newPassword;
}
