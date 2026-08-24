package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.AchievementVisibility;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementResponse {

    private Long id;

    private Long userId;
    private String facultyName;
    private String facultyEmail;
    private String employeeId;
    private String departmentCode;
    private String departmentName;

    private Long categoryId;
    private String categoryCode;
    private String categoryName;

    private String title;
    private String description;
    private String keywords;
    private LocalDate achievementDate;
    private String academicYear;

    private AchievementStatus status;

    /**
     * The owner's own visibility setting.
     *
     * <p>Safe here and only here. This DTO is returned exclusively to
     * authenticated callers — the owner, their HOD, or an administrator — and
     * every one of them needs to see it: the owner to know whether their work is
     * published, an HOD and an admin to understand what they are looking at.
     *
     * <p>The public side never uses this class. It has its own DTOs in
     * {@code dto.publicview}, which is the whole reason those exist.
     */
    private AchievementVisibility visibility;

    private String verificationComment;
    private Long verifiedByUserId;
    private String verifiedByName;
    private LocalDateTime verifiedAt;

    private String proofDocumentUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AchievementResponse fromEntity(com.niet.facultyachievement.entity.Achievement achievement) {
        if (achievement == null) return null;
        var user = achievement.getUser();
        var category = achievement.getCategory();
        var verifier = achievement.getVerifiedBy();

        return AchievementResponse.builder()
                .id(achievement.getId())
                .userId(user != null ? user.getId() : null)
                .facultyName(user != null ? user.getFullName() : null)
                .facultyEmail(user != null ? user.getEmail() : null)
                .employeeId(user != null ? user.getEmployeeId() : null)
                .departmentCode(user != null && user.getDepartment() != null ? user.getDepartment().getCode() : null)
                .departmentName(user != null && user.getDepartment() != null ? user.getDepartment().getName() : null)
                .categoryId(category != null ? category.getId() : null)
                .categoryCode(category != null ? category.getCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .title(achievement.getTitle())
                .description(achievement.getDescription())
                .keywords(achievement.getKeywords())
                .achievementDate(achievement.getAchievementDate())
                .academicYear(achievement.getAcademicYear())
                .status(achievement.getStatus())
                .visibility(achievement.getVisibility())
                .verificationComment(achievement.getVerificationComment())
                .verifiedByUserId(verifier != null ? verifier.getId() : null)
                .verifiedByName(verifier != null ? verifier.getFullName() : null)
                .verifiedAt(achievement.getVerifiedAt())
                .proofDocumentUrl(achievement.getProofDocumentUrl())
                .createdAt(achievement.getCreatedAt())
                .updatedAt(achievement.getUpdatedAt())
                .build();
    }
}
