package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Create or rename a department. Used by both POST and PUT, because the fields
 * an administrator may set are identical in each case.
 *
 * <p>The code is restricted to letters, digits, dashes and underscores because
 * it is the short handle shown in filters and badges throughout the portal, and
 * because Part B will use department codes in public URLs. Keeping it simple now
 * avoids having to migrate awkward existing values later.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentRequest {

    @NotBlank(message = "Department code is required")
    @Size(max = 20, message = "Department code must not exceed 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Department code may only contain letters, numbers, dashes and underscores")
    private String code;

    @NotBlank(message = "Department name is required")
    @Size(min = 2, max = 100, message = "Department name must be between 2 and 100 characters")
    private String name;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
