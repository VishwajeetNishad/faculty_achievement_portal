package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.AchievementStatus;
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
    private LocalDate achievementDate;
    private String academicYear;

    private AchievementStatus status;

    private String verificationComment;
    private Long verifiedByUserId;
    private String verifiedByName;
    private LocalDateTime verifiedAt;

    private String proofDocumentUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
