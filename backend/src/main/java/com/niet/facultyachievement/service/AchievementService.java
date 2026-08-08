package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.dto.AchievementVerificationRequest;
import com.niet.facultyachievement.entity.AchievementStatus;

import java.util.List;

public interface AchievementService {
    AchievementResponse createAchievement(Long userId, AchievementCreateRequest request);
    AchievementResponse getAchievementById(Long id);
    List<AchievementResponse> getAchievementsByUser(Long userId);
    List<AchievementResponse> getAchievementsByStatus(AchievementStatus status);
    List<AchievementResponse> getAchievementsByDepartment(Long departmentId);
    List<AchievementResponse> getAllAchievements();
    AchievementResponse updateAchievement(Long id, Long userId, AchievementUpdateRequest request);
    void deleteAchievement(Long id, Long userId);
    AchievementResponse verifyAchievement(Long id, Long reviewerUserId, AchievementVerificationRequest request);
}
