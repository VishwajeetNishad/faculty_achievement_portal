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
public class AchievementCreateRequest {

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

    /**
     * Comma-separated subject terms the author chooses, used by public search.
     * Optional — a record with no keywords is still findable by title.
     */
    @Size(max = 500, message = "Keywords cannot exceed 500 characters")
    private String keywords;

    /**
     * Who may read this record once it is approved.
     *
     * <p>Deliberately <strong>not</strong> {@code @NotNull}. The portal's own
     * add-achievement form always sends it, but leaving it optional means an
     * older client, a saved bookmark or an integration written before Track B
     * still works — and lands on {@code PRIVATE}, because the service defaults a
     * missing value rather than trusting a blank. The failure mode of a forgotten
     * field is then "nothing was published", never "something was published by
     * accident".
     */
    private AchievementVisibility visibility;
}
