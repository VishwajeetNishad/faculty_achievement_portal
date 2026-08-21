package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.dto.AchievementVerificationRequest;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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

    /**
     * Server-side search with dynamic filtering, sorting and pagination.
     * The currentUser parameter is used to enforce data-scope authorization:
     *   FACULTY -> only their own achievements
     *   HOD     -> only their department achievements
     *   ADMIN   -> institution-wide
     *
     * @param keyword       optional title/description keyword
     * @param status        optional status filter (PENDING/APPROVED/REJECTED)
     * @param categoryId    optional category ID filter
     * @param categoryCode  optional category code filter (used when categoryId is null)
     * @param academicYear  optional academic year exact match
     * @param fromDate      optional lower bound on achievementDate
     * @param toDate        optional upper bound on achievementDate
     * @param page          0-indexed page number
     * @param size          page size (1–100)
     * @param sortBy        whitelisted sort field name
     * @param sortDir       "asc" or "desc"
     * @param currentUser   authenticated user driving authorization
     */
    PagedResponse<AchievementResponse> searchAchievements(
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
    );

    /**
     * Export achievements matching same filters and scope as searchAchievements to CSV bytes.
     * Authorization scope is enforced server-side, not from request parameters.
     */
    byte[] exportAchievementsCsv(
            String keyword,
            AchievementStatus status,
            Long categoryId,
            String categoryCode,
            String academicYear,
            LocalDate fromDate,
            LocalDate toDate,
            Long filterDepartmentId,
            User currentUser
    );
}
