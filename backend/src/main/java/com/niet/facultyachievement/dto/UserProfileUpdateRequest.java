package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Restricted DTO for faculty self-profile editing.
 * Only contains fields that faculty are allowed to modify.
 * Does NOT contain: id, employeeId, email, role, department, status, passwordHash.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileUpdateRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;
}
