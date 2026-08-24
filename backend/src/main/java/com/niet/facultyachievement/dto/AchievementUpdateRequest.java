package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.AchievementVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementUpdateRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Achievement date is required")
    @PastOrPresent(message = "Achievement date cannot be in the future")
    private LocalDate achievementDate;

    @NotBlank(message = "Academic year is required")
    @Size(max = 20, message = "Academic year cannot exceed 20 characters")
    private String academicYear;

    @Size(max = 500, message = "Proof document URL cannot exceed 500 characters")
    private String proofDocumentUrl;

    @Size(max = 500, message = "Keywords cannot exceed 500 characters")
    private String keywords;

    /**
     * The new visibility, or {@code null} to leave it exactly as it is.
     *
     * <p>Note the difference from create: on an update, a missing value means
     * "no change", not "make it PRIVATE". Forcing PRIVATE here would silently
     * un-publish a faculty member's work every time an older client edited a
     * title, which is a worse outcome than leaving the setting alone.
     */
    private AchievementVisibility visibility;
}
