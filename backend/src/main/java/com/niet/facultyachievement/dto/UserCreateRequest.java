package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * What an administrator sends to create a new portal account.
 *
 * <p>The account being created is described here, but WHO is creating it is
 * never part of this object — the acting administrator is always taken from the
 * JWT via {@code SecurityContextHolder}. If the actor were a field here, anyone
 * could claim to be an administrator simply by editing the request body.
 *
 * <p>{@code role} and {@code status} are plain strings rather than enums so that
 * an unrecognised value produces a clear 400 "unknown role" message from the
 * service, instead of Jackson failing to parse the whole request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateRequest {

    @NotBlank(message = "Employee ID is required")
    @Size(max = 50, message = "Employee ID must not exceed 50 characters")
    private String employeeId;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    /**
     * The starting password. Hashed with BCrypt before it is stored and never
     * echoed back in any response, log line or audit entry.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Designation is required")
    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;

    /** Mandatory — every user in this portal belongs to exactly one department. */
    @NotNull(message = "Department is required")
    private Long departmentId;

    /** ROLE_FACULTY, ROLE_HOD or ROLE_ADMIN (the ROLE_ prefix is optional). */
    @NotBlank(message = "Role is required")
    private String role;

    /**
     * Optional. Defaults to ACTIVE. Creating an account in any other state
     * requires the MANAGE_USER_STATUS permission, because it is the same
     * decision as deactivating an account.
     */
    private String status;
}
