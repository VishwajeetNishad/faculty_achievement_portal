package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.dto.AchievementVerificationRequest;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementCategory;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.NotificationType;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementCategoryRepository;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.specification.AchievementSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

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

        // Audit achievement creation
        auditLogService.logAction(
                AuditAction.ACHIEVEMENT_CREATED,
                "ACHIEVEMENT",
                saved.getId(),
                "Created achievement record: '" + saved.getTitle() + "'",
                user,
                null
        );

        // 1. Notify submitting faculty member
        notificationService.createNotification(
                user,
                "Achievement Record Submitted",
                "Your achievement '" + saved.getTitle() + "' has been submitted successfully and is pending verification.",
                NotificationType.ACHIEVEMENT_SUBMITTED,
                saved
        );

        // 2. Notify HOD(s) of the faculty's department
        if (user.getDepartment() != null) {
            String deptName = user.getDepartment().getName();
            notificationService.notifyDepartmentHods(
                    user.getDepartment().getId(),
                    "Achievement Pending Review",
                    user.getFullName() + " (" + deptName + ") submitted an achievement '" + saved.getTitle() + "' requiring verification.",
                    NotificationType.VERIFICATION_REQUIRED,
                    saved
            );
        }

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

        // Audit achievement update
        auditLogService.logAction(
                AuditAction.ACHIEVEMENT_UPDATED,
                "ACHIEVEMENT",
                updated.getId(),
                "Updated achievement record: '" + updated.getTitle() + "'",
                achievement.getUser(),
                null
        );

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

        // Audit achievement deletion
        auditLogService.logAction(
                AuditAction.ACHIEVEMENT_DELETED,
                "ACHIEVEMENT",
                id,
                "Deleted achievement record id: " + id + " ('" + achievement.getTitle() + "')",
                achievement.getUser(),
                null
        );
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

        // Audit verification action
        if (request.getStatus() == AchievementStatus.APPROVED) {
            auditLogService.logAction(
                    AuditAction.ACHIEVEMENT_APPROVED,
                    "ACHIEVEMENT",
                    updated.getId(),
                    "Verified and APPROVED achievement: '" + updated.getTitle() + "'",
                    reviewer,
                    null
            );
            notificationService.createNotification(
                    updated.getUser(),
                    "Achievement Record Approved",
                    "Your achievement '" + updated.getTitle() + "' has been officially verified and APPROVED by " + reviewer.getFullName() + ".",
                    NotificationType.ACHIEVEMENT_APPROVED,
                    updated
            );
        } else if (request.getStatus() == AchievementStatus.REJECTED) {
            String comment = request.getVerificationComment() != null ? request.getVerificationComment().trim() : "No comment provided";
            auditLogService.logAction(
                    AuditAction.ACHIEVEMENT_REJECTED,
                    "ACHIEVEMENT",
                    updated.getId(),
                    "Rejected achievement: '" + updated.getTitle() + "'. Feedback: " + comment,
                    reviewer,
                    null
            );
            notificationService.createNotification(
                    updated.getUser(),
                    "Achievement Record Rejected",
                    "Your achievement '" + updated.getTitle() + "' was rejected by " + reviewer.getFullName() + ". Feedback: " + comment,
                    NotificationType.ACHIEVEMENT_REJECTED,
                    updated
            );
        }

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

        // Audit proof upload
        auditLogService.logAction(
                AuditAction.PROOF_UPLOADED,
                "PROOF",
                updated.getId(),
                "Uploaded proof PDF document for achievement id: " + id,
                updated.getUser(),
                null
        );

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

        // Audit proof deletion
        auditLogService.logAction(
                AuditAction.PROOF_DELETED,
                "PROOF",
                id,
                "Deleted proof PDF document for achievement id: " + id,
                achievement.getUser(),
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AchievementResponse> searchAchievements(
            String keyword,
            AchievementStatus status,
            Long categoryId,
            String categoryCode,
            String academicYear,
            LocalDate fromDate,
            LocalDate toDate,
            Long filterDepartmentId,
            int page,
            int size,
            String sortBy,
            String sortDir,
            User currentUser
    ) {
        // Validate and cap page parameters
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        // Validate sort field whitelist
        if (!AchievementSpecification.isSortFieldAllowed(sortBy)) {
            throw new BadRequestException("Sort field '" + sortBy + "' is not permitted. Allowed: achievementDate, createdAt, title, academicYear, status");
        }

        // Validate date range
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }

        String effectiveSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy.trim();
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, effectiveSortBy));

        // Build authorization scope specification (derived from JWT role — never from request params)
        Specification<Achievement> scopeSpec = buildScopeSpec(currentUser);

        // Build filter specification from client query params
        // filterDepartmentId is only effective for ADMIN (scope = institution-wide);
        // for FACULTY/HOD the scope spec already restricts to their own data
        Specification<Achievement> filterSpec = AchievementSpecification.withFilters(
                keyword, status, categoryId, categoryCode, academicYear, fromDate, toDate, filterDepartmentId);

        // Combine: scope ALWAYS AND-ed first, filter on top
        Specification<Achievement> combinedSpec = Specification.where(scopeSpec).and(filterSpec);

        Page<Achievement> resultPage = achievementRepository.findAll(combinedSpec, pageable);
        return PagedResponse.from(resultPage, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportAchievementsCsv(
            String keyword,
            AchievementStatus status,
            Long categoryId,
            String categoryCode,
            String academicYear,
            LocalDate fromDate,
            LocalDate toDate,
            Long filterDepartmentId,
            User currentUser
    ) {
        // Validate date range
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }

        Specification<Achievement> scopeSpec = buildScopeSpec(currentUser);
        Specification<Achievement> filterSpec = AchievementSpecification.withFilters(
                keyword, status, categoryId, categoryCode, academicYear, fromDate, toDate, filterDepartmentId);
        Specification<Achievement> combinedSpec = Specification.where(scopeSpec).and(filterSpec);

        // Use a large pageable to stream all matching (capped at 5000 rows for safety)
        Pageable pageable = PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Achievement> resultPage = achievementRepository.findAll(combinedSpec, pageable);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Write UTF-8 BOM so Excel auto-detects encoding
        baos.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (PrintWriter pw = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            pw.println("ID,Faculty Name,Employee ID,Department,Category,Title,Academic Year,Achievement Date,Status,Verification Comment,Created At");
            for (Achievement a : resultPage.getContent()) {
                pw.println(String.join(",",
                        csvCell(String.valueOf(a.getId())),
                        csvCell(a.getUser() != null ? a.getUser().getFullName() : ""),
                        csvCell(a.getUser() != null ? a.getUser().getEmployeeId() : ""),
                        csvCell(a.getUser() != null && a.getUser().getDepartment() != null ? a.getUser().getDepartment().getName() : ""),
                        csvCell(a.getCategory() != null ? a.getCategory().getCategoryName() : ""),
                        csvCell(a.getTitle()),
                        csvCell(a.getAcademicYear()),
                        csvCell(a.getAchievementDate() != null ? a.getAchievementDate().toString() : ""),
                        csvCell(a.getStatus() != null ? a.getStatus().name() : ""),
                        csvCell(a.getVerificationComment()),
                        csvCell(a.getCreatedAt() != null ? a.getCreatedAt().toString() : "")
                ));
            }
        }
        return baos.toByteArray();
    }

    /**
     * Build scope specification based on role:
     *   FACULTY -> their own achievements only
     *   HOD     -> their department only
     *   ADMIN   -> no restriction (null spec = match all)
     */
    private Specification<Achievement> buildScopeSpec(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "";
        boolean isAdmin = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN");
        boolean isHod   = roleName.equalsIgnoreCase("HOD")   || roleName.equalsIgnoreCase("ROLE_HOD");

        if (isAdmin) {
            return Specification.where(null); // institution-wide
        } else if (isHod) {
            if (user.getDepartment() == null) {
                throw new BadRequestException("HOD user has no department assigned");
            }
            return AchievementSpecification.forDepartment(user.getDepartment().getId());
        } else {
            // FACULTY or any other role: restrict to own records only
            return AchievementSpecification.forUser(user.getId());
        }
    }

    private String csvCell(String value) {
        if (value == null) return "";
        // Escape double-quotes by doubling them and wrap cell in quotes if needed
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
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
