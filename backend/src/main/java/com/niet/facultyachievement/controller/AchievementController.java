package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.dto.AchievementVerificationRequest;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST Controller for Faculty Achievement Management, Proof Uploads & Verification Workflow.
 * Base Endpoint: /api/achievements
 */
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final UserRepository userRepository;

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User is not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user record not found"));
    }

    @PostMapping
    public ResponseEntity<AchievementResponse> createAchievement(
            Authentication authentication,
            @Valid @RequestBody AchievementCreateRequest request) {
        User currentUser = getAuthenticatedUser(authentication);
        AchievementResponse response = achievementService.createAchievement(currentUser.getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<List<AchievementResponse>> getMyAchievements(Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        List<AchievementResponse> responses = achievementService.getAchievementsByUser(currentUser.getId());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AchievementResponse> getAchievementById(
            Authentication authentication,
            @PathVariable("id") Long id) {
        User currentUser = getAuthenticatedUser(authentication);
        AchievementResponse response = achievementService.getAchievementById(id);
        
        boolean isOwner = response.getUserId().equals(currentUser.getId());
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdminOrHod = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN") ||
                               roleName.equalsIgnoreCase("HOD") || roleName.equalsIgnoreCase("ROLE_HOD");

        if (!isOwner && !isAdminOrHod) {
            throw new AccessDeniedException("You are not authorized to view this achievement record");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AchievementResponse>> getAchievementsByUser(
            Authentication authentication,
            @PathVariable("userId") Long userId) {
        User currentUser = getAuthenticatedUser(authentication);
        
        boolean isSelf = currentUser.getId().equals(userId);
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdminOrHod = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN") ||
                               roleName.equalsIgnoreCase("HOD") || roleName.equalsIgnoreCase("ROLE_HOD");

        if (!isSelf && !isAdminOrHod) {
            throw new AccessDeniedException("You are not authorized to view achievements belonging to user id: " + userId);
        }

        List<AchievementResponse> responses = achievementService.getAchievementsByUser(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<AchievementResponse>> getAchievementsByStatus(@PathVariable("status") String status) {
        AchievementStatus achievementStatus;
        try {
            achievementStatus = AchievementStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid achievement status: " + status + ". Allowed values: PENDING, APPROVED, REJECTED.");
        }
        List<AchievementResponse> responses = achievementService.getAchievementsByStatus(achievementStatus);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<AchievementResponse>> getAchievementsByDepartment(
            Authentication authentication,
            @PathVariable("departmentId") Long departmentId) {
        User currentUser = getAuthenticatedUser(authentication);
        
        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : "";
        boolean isAdmin = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN");
        boolean isOwnDeptHod = (roleName.equalsIgnoreCase("HOD") || roleName.equalsIgnoreCase("ROLE_HOD")) && 
                currentUser.getDepartment() != null && currentUser.getDepartment().getId().equals(departmentId);

        if (!isAdmin && !isOwnDeptHod) {
            throw new AccessDeniedException("You are not authorized to view achievements for department id: " + departmentId);
        }

        List<AchievementResponse> responses = achievementService.getAchievementsByDepartment(departmentId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AchievementResponse> updateAchievement(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody AchievementUpdateRequest request) {
        User currentUser = getAuthenticatedUser(authentication);
        AchievementResponse response = achievementService.updateAchievement(id, currentUser.getId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAchievement(
            Authentication authentication,
            @PathVariable("id") Long id) {
        User currentUser = getAuthenticatedUser(authentication);
        achievementService.deleteAchievement(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/verification")
    public ResponseEntity<AchievementResponse> verifyAchievement(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody AchievementVerificationRequest request) {

        User reviewer = getAuthenticatedUser(authentication);
        String roleName = reviewer.getRole() != null ? reviewer.getRole().getName() : "";
        boolean isAdmin = roleName.equalsIgnoreCase("ADMIN") || roleName.equalsIgnoreCase("ROLE_ADMIN");
        boolean isHod = roleName.equalsIgnoreCase("HOD") || roleName.equalsIgnoreCase("ROLE_HOD");

        if (!isAdmin && !isHod) {
            throw new AccessDeniedException("Faculty members are not authorized to verify achievement records");
        }

        AchievementResponse target = achievementService.getAchievementById(id);

        if (isHod && !isAdmin) {
            boolean matchesDept = reviewer.getDepartment() != null && target.getDepartmentCode() != null
                    && reviewer.getDepartment().getCode().equalsIgnoreCase(target.getDepartmentCode());

            if (!matchesDept) {
                throw new AccessDeniedException("HOD is not authorized to verify achievements belonging to other departments");
            }
        }

        AchievementResponse response = achievementService.verifyAchievement(id, reviewer.getId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/achievements/{id}/proof
     * Secure Multipart File Upload Endpoint for PDF Proof Documents
     */
    @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AchievementResponse> uploadProofDocument(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) {
        User currentUser = getAuthenticatedUser(authentication);
        AchievementResponse response = achievementService.uploadProofDocument(id, currentUser.getId(), file);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/achievements/{id}/proof
     * Protected Download / View Endpoint for PDF Proof Documents
     */
    @GetMapping("/{id}/proof")
    public ResponseEntity<Resource> getProofDocument(
            Authentication authentication,
            @PathVariable("id") Long id) {
        User currentUser = getAuthenticatedUser(authentication);
        Resource fileResource = achievementService.getProofDocumentResource(id, currentUser.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"achievement_proof_" + id + ".pdf\"")
                .body(fileResource);
    }

    /**
     * DELETE /api/achievements/{id}/proof
     * Secure Deletion Endpoint for PDF Proof Documents
     */
    @DeleteMapping("/{id}/proof")
    public ResponseEntity<Void> deleteProofDocument(
            Authentication authentication,
            @PathVariable("id") Long id) {
        User currentUser = getAuthenticatedUser(authentication);
        achievementService.deleteProofDocument(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
