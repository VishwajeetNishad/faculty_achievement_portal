package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AchievementCategoryRepository categoryRepository;

    @InjectMocks
    private AchievementServiceImpl achievementService;

    private User sampleUser;
    private User reviewerUser;
    private AchievementCategory sampleCategory;
    private Achievement sampleAchievement;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .employeeId("EMP001")
                .fullName("Dr. Sharma")
                .email("sharma@niet.co.in")
                .build();

        reviewerUser = User.builder()
                .id(2L)
                .employeeId("EMP002")
                .fullName("Dr. Admin")
                .email("admin@faculty.edu")
                .build();

        sampleCategory = AchievementCategory.builder()
                .id(1L)
                .code("PUBLICATION")
                .categoryName("Research Publication")
                .build();

        sampleAchievement = Achievement.builder()
                .id(100L)
                .user(sampleUser)
                .category(sampleCategory)
                .title("Deep Learning in Healthcare")
                .achievementDate(LocalDate.of(2025, 5, 10))
                .academicYear("2025-2026")
                .status(AchievementStatus.PENDING)
                .build();
    }

    @Test
    void createAchievement_Success() {
        AchievementCreateRequest request = AchievementCreateRequest.builder()
                .categoryId(1L)
                .title("Deep Learning in Healthcare")
                .achievementDate(LocalDate.of(2025, 5, 10))
                .academicYear("2025-2026")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(achievementRepository.save(any(Achievement.class))).thenReturn(sampleAchievement);

        AchievementResponse response = achievementService.createAchievement(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Deep Learning in Healthcare", response.getTitle());
        assertEquals(AchievementStatus.PENDING, response.getStatus());
        verify(achievementRepository, times(1)).save(any(Achievement.class));
    }

    @Test
    void createAchievement_UserNotFound_ThrowsException() {
        AchievementCreateRequest request = AchievementCreateRequest.builder()
                .categoryId(1L)
                .title("Test Title")
                .build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                achievementService.createAchievement(99L, request));
    }

    @Test
    void getAchievementById_Success() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(sampleAchievement));

        AchievementResponse response = achievementService.getAchievementById(100L);

        assertNotNull(response);
        assertEquals("Deep Learning in Healthcare", response.getTitle());
    }

    @Test
    void getAchievementById_NotFound_ThrowsException() {
        when(achievementRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                achievementService.getAchievementById(999L));
    }

    @Test
    void deleteAchievement_Success() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(sampleAchievement));

        achievementService.deleteAchievement(100L, 1L);

        verify(achievementRepository, times(1)).delete(sampleAchievement);
    }

    @Test
    void deleteAchievement_OwnershipMismatch_ThrowsAccessDeniedException() {
        when(achievementRepository.findById(100L)).thenReturn(Optional.of(sampleAchievement));

        assertThrows(AccessDeniedException.class, () ->
                achievementService.deleteAchievement(100L, 999L));
    }

    @Test
    void verifyAchievement_Approval_Success() {
        AchievementVerificationRequest request = AchievementVerificationRequest.builder()
                .status(AchievementStatus.APPROVED)
                .verificationComment("Approved by reviewer")
                .build();

        when(achievementRepository.findById(100L)).thenReturn(Optional.of(sampleAchievement));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewerUser));
        when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AchievementResponse response = achievementService.verifyAchievement(100L, 2L, request);

        assertNotNull(response);
        assertEquals(AchievementStatus.APPROVED, response.getStatus());
        assertEquals("Approved by reviewer", response.getVerificationComment());
        assertEquals(2L, response.getVerifiedByUserId());
        assertNotNull(response.getVerifiedAt());
    }

    @Test
    void verifyAchievement_AlreadyVerified_ThrowsBadRequestException() {
        sampleAchievement.setStatus(AchievementStatus.APPROVED);
        AchievementVerificationRequest request = AchievementVerificationRequest.builder()
                .status(AchievementStatus.APPROVED)
                .build();

        when(achievementRepository.findById(100L)).thenReturn(Optional.of(sampleAchievement));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewerUser));

        assertThrows(BadRequestException.class, () ->
                achievementService.verifyAchievement(100L, 2L, request));
    }
}
