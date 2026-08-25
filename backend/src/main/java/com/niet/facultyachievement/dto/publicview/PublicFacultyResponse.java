package com.niet.facultyachievement.dto.publicview;

import com.niet.facultyachievement.entity.User;
import lombok.*;

/**
 * One person in the public faculty directory.
 *
 * <p>Five identity fields and two counts. That is the whole of what an
 * anonymous visitor gets about a member of staff, and it is chosen to match
 * what a university already prints in a printed prospectus: name, title,
 * department.
 *
 * <p>Deliberately absent: {@code email}, {@code phone}, {@code employeeId},
 * {@code passwordHash}, the internal {@code id}, the account {@code status} and
 * the {@code role}. Email and phone are contact details the person did not
 * consent to publishing; employee id and the internal id are identifiers that
 * belong inside the institution; status and role describe the account, not the
 * academic. {@link #slug} is the only addressing information exposed, and it
 * exists precisely so the internal id does not have to be.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicFacultyResponse {

    /** Public address of this person's profile, e.g. {@code /faculty/{slug}}. */
    private String slug;

    private String fullName;
    private String designation;
    private String departmentCode;
    private String departmentName;

    /**
     * How many of this person's achievements are publicly visible — that is,
     * APPROVED and PUBLIC. Never a total of everything they own.
     *
     * <p>Getting this wrong would be a quiet but real leak: a card reading
     * "12 achievements" next to a profile page listing two would tell a visitor
     * that ten records exist which they are not allowed to see. The count and
     * the list are therefore produced by the same filter.
     */
    private long publicAchievementCount;

    /** The publication subset of the count above, shown on the directory card. */
    private long publicationCount;

    /**
     * Identity fields only — counts are supplied by the caller because they
     * come from a single grouped query rather than from walking each user's
     * achievements one at a time.
     */
    public static PublicFacultyResponse fromEntity(User user, long achievementCount, long publicationCount) {
        if (user == null) return null;
        var department = user.getDepartment();

        return PublicFacultyResponse.builder()
                .slug(user.getPublicSlug())
                .fullName(user.getFullName())
                .designation(user.getDesignation())
                .departmentCode(department != null ? department.getCode() : null)
                .departmentName(department != null ? department.getName() : null)
                .publicAchievementCount(achievementCount)
                .publicationCount(publicationCount)
                .build();
    }
}
