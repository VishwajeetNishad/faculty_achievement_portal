package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.AchievementStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementVerificationRequest {

    @NotNull(message = "Verification status is required (APPROVED or REJECTED)")
    private AchievementStatus status;

    private String verificationComment;
}
