package com.niet.facultyachievement.dto.publicview;

import com.niet.facultyachievement.entity.User;
import lombok.*;

import java.util.Map;

/**
 * The header of a public faculty profile page.
 *
 * <p>Carries the same identity fields as {@link PublicFacultyResponse} plus a
 * per-category breakdown, which the profile page turns into the "areas of work"
 * chips and the category tabs.
 *
 * <p>The breakdown is derived from real records rather than from a stored list
 * of research interests, because there is no such column on {@code users} —
 * the {@code User} entity has no bio, no photo, no interests and no education
 * history. Inventing those fields to fill a design was not an option, so the
 * page shows what the data actually supports: the subject areas this person has
 * genuinely published in, counted from their public achievements.
 *
 * <p>Same omissions as the directory DTO — no email, phone, employee id,
 * internal id, account status or role.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicFacultyProfileResponse {

    private String slug;
    private String fullName;
    private String designation;
    private String departmentCode;
    private String departmentName;

    /** Total public achievements. Equals the sum of {@link #categoryCounts}. */
    private long publicAchievementCount;

    /**
     * Public achievement count keyed by category code — {@code PUBLICATION},
     * {@code PATENT}, {@code RESEARCH_GRANT}, {@code WORKSHOP_FDP},
     * {@code AWARD}. Categories with nothing public are simply absent rather
     * than present with a zero, so the page renders no empty chips.
     */
    private Map<String, Long> categoryCounts;

    public static PublicFacultyProfileResponse fromEntity(User user,
                                                          long publicAchievementCount,
                                                          Map<String, Long> categoryCounts) {
        if (user == null) return null;
        var department = user.getDepartment();

        return PublicFacultyProfileResponse.builder()
                .slug(user.getPublicSlug())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .departmentCode(department != null ? department.getCode() : null)
                .departmentName(department != null ? department.getName() : null)
                .publicAchievementCount(publicAchievementCount)
                .categoryCounts(categoryCounts)
                .build();
    }
}
