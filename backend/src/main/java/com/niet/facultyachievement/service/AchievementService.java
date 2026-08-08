package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.dto.AchievementVerificationRequest;
import com.niet.facultyachievement.entity.AchievementStatus;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

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
    
    AchievementResponse uploadProofDocument(Long id, Long userId, MultipartFile file);
    Resource getProofDocumentResource(Long id, Long requestingUserId);
    void deleteProofDocument(Long id, Long userId);
}
