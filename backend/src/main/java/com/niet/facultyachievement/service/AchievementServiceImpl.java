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
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserRepository userRepository;
    private final AchievementCategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

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

        // Clean up physical proof document file if stored
        String proofUrl = achievement.getProofDocumentUrl();
        if (proofUrl != null && proofUrl.contains("proof_")) {
            String filename = extractFilenameFromUrl(proofUrl);
            if (filename != null) {
                fileStorageService.deleteFile(filename);
            }
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

        if (achievement.getStatus() != AchievementStatus.PENDING) {
            throw new BadRequestException("Achievement has already been reviewed and is currently " + achievement.getStatus());
        }

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

    @Override
    @Transactional
    public AchievementResponse uploadProofDocument(Long id, Long userId, MultipartFile file) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));

        if (!achievement.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User with id " + userId + " does not own achievement with id " + id);
        }

        if (achievement.getStatus() == AchievementStatus.APPROVED) {
            throw new BadRequestException("Cannot modify or replace proof document for an officially APPROVED achievement record");
        }

        // Store physical file safely
        String safeFilename = fileStorageService.storeFile(file);

        // Delete old file if replacing
        String oldProofUrl = achievement.getProofDocumentUrl();
        if (oldProofUrl != null && oldProofUrl.contains("proof_")) {
            String oldFilename = extractFilenameFromUrl(oldProofUrl);
            if (oldFilename != null) {
                fileStorageService.deleteFile(oldFilename);
            }
        }

        // Store application-relative reference API URL (NEVER absolute Windows path!)
        String relativeProofUrl = "/api/achievements/" + id + "/proof?file=" + safeFilename;
        achievement.setProofDocumentUrl(relativeProofUrl);

        Achievement updated = achievementRepository.save(achievement);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource getProofDocumentResource(Long id, Long requestingUserId) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));

        User requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Requesting user not found with id: " + requestingUserId));

        // Authorization check: Owner, Admin, or HOD of department
        boolean isOwner = achievement.getUser().getId().equals(requestingUserId);
        String roleName = requestingUser.getRole() != null ? requestingUser.getRole().getName() : "";
        boolean isAdmin = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN");
        boolean isOwnDeptHod = (roleName.equalsIgnoreCase("HOD") || roleName.equalsIgnoreCase("ROLE_HOD")) &&
                requestingUser.getDepartment() != null && achievement.getUser().getDepartment() != null &&
                requestingUser.getDepartment().getId().equals(achievement.getUser().getDepartment().getId());

        if (!isOwner && !isAdmin && !isOwnDeptHod) {
            throw new AccessDeniedException("You are not authorized to access proof documents for this achievement");
        }

        String proofUrl = achievement.getProofDocumentUrl();
        if (proofUrl == null || proofUrl.trim().isEmpty()) {
            throw new ResourceNotFoundException("No proof document associated with achievement id: " + id);
        }

        String filename = extractFilenameFromUrl(proofUrl);
        if (filename == null) {
            throw new ResourceNotFoundException("Invalid proof document reference for achievement id: " + id);
        }

        return fileStorageService.loadFileAsResource(filename);
    }

    @Override
    @Transactional
    public void deleteProofDocument(Long id, Long userId) {
        Achievement achievement = achievementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found with id: " + id));

        if (!achievement.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User with id " + userId + " does not own achievement with id " + id);
        }

        if (achievement.getStatus() == AchievementStatus.APPROVED) {
            throw new BadRequestException("Cannot delete proof document from an officially APPROVED achievement record");
        }

        String proofUrl = achievement.getProofDocumentUrl();
        if (proofUrl != null) {
            String filename = extractFilenameFromUrl(proofUrl);
            if (filename != null) {
                fileStorageService.deleteFile(filename);
            }
        }

        achievement.setProofDocumentUrl(null);
        achievementRepository.save(achievement);
    }

    private String extractFilenameFromUrl(String url) {
        if (url == null) return null;
        if (url.contains("file=")) {
            return url.substring(url.indexOf("file=") + 5);
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
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
