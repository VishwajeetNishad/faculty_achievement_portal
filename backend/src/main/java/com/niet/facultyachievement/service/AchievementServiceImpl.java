package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.dto.AchievementVerificationRequest;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementCategory;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementCategoryRepository;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserRepository userRepository;
    private final AchievementCategoryRepository categoryRepository;

    @Override
    @Transactional
    public AchievementResponse createAchievement(Long userId, AchievementCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        AchievementCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Achievement category not found with id: " + request.getCategoryId()));

        Achievement achievement = Achievement.builder()
                .user(user)
                .category(category)
                .title(request.getTitle())
                .description(request.getDescription())
                .achievementDate(request.getAchievementDate())
                .academicYear(request.getAcademicYear())
                .status(AchievementStatus.PENDING)
                .proofDocumentUrl(request.getProofDocumentUrl())
                .build();

        Achievement saved = achievementRepository.save(achievement);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AchievementResponse getAchievementById(Long id) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));
        return mapToResponse(achievement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievementsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return achievementRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievementsByStatus(AchievementStatus status) {
        return achievementRepository.findByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievementsByDepartment(Long departmentId) {
        return achievementRepository.findByUserDepartmentId(departmentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementResponse> getAllAchievements() {
        return achievementRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AchievementResponse updateAchievement(Long id, Long userId, AchievementUpdateRequest request) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));

        if (!achievement.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User with id " + userId + " does not own achievement with id " + id);
        }

        AchievementCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Achievement category not found with id: " + request.getCategoryId()));

        achievement.setCategory(category);
        achievement.setTitle(request.getTitle());
        achievement.setDescription(request.getDescription());
        achievement.setAchievementDate(request.getAchievementDate());
        achievement.setAcademicYear(request.getAcademicYear());
        achievement.setProofDocumentUrl(request.getProofDocumentUrl());

        Achievement updated = achievementRepository.save(achievement);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteAchievement(Long id, Long userId) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));

        if (!achievement.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User with id " + userId + " does not own achievement with id " + id);
        }

        achievementRepository.delete(achievement);
    }

    @Override
    @Transactional
    public AchievementResponse verifyAchievement(Long id, Long reviewerUserId, AchievementVerificationRequest request) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));

        User reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found with id: " + reviewerUserId));

        // Rule 1: Controlled status transition — Only PENDING records can be verified
        if (achievement.getStatus() != AchievementStatus.PENDING) {
            throw new BadRequestException("Achievement has already been reviewed and is currently " + achievement.getStatus());
        }

        // Rule 2: Require review comment when status is REJECTED
        if (request.getStatus() == AchievementStatus.REJECTED && 
           (request.getVerificationComment() == null || request.getVerificationComment().trim().isEmpty())) {
            throw new BadRequestException("A verification review comment is required when rejecting an achievement record");
        }

        achievement.setStatus(request.getStatus());
        achievement.setVerificationComment(request.getVerificationComment() != null ? request.getVerificationComment().trim() : null);
        achievement.setVerifiedBy(reviewer);
        achievement.setVerifiedAt(LocalDateTime.now());

        Achievement updated = achievementRepository.save(achievement);
        return mapToResponse(updated);
    }

    private AchievementResponse mapToResponse(Achievement achievement) {
        User user = achievement.getUser();
        AchievementCategory category = achievement.getCategory();
        User verifier = achievement.getVerifiedBy();

        return AchievementResponse.builder()
                .id(achievement.getId())
                .userId(user.getId())
                .facultyName(user.getFullName())
                .facultyEmail(user.getEmail())
                .employeeId(user.getEmployeeId())
                .departmentCode(user.getDepartment() != null ? user.getDepartment().getCode() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .categoryId(category.getId())
                .categoryCode(category.getCode())
                .categoryName(category.getCategoryName())
                .title(achievement.getTitle())
                .description(achievement.getDescription())
                .achievementDate(achievement.getAchievementDate())
                .academicYear(achievement.getAcademicYear())
                .status(achievement.getStatus())
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
