package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Faculty Achievement Management.
 * Secured via Spring Security + JWT Authentication context.
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
        
        // Ownership Check / Role Permission (Faculty A cannot read Faculty B's private achievement unless ADMIN/HOD)
        boolean isOwner = response.getUserId().equals(currentUser.getId());
        boolean isAdminOrHod = currentUser.getRole() != null && 
                (currentUser.getRole().getName().equalsIgnoreCase("ADMIN") || currentUser.getRole().getName().equalsIgnoreCase("HOD"));

        if (!isOwner && !isAdminOrHod) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AchievementResponse>> getAchievementsByUser(
            Authentication authentication,
            @PathVariable("userId") Long userId) {
        User currentUser = getAuthenticatedUser(authentication);
        
        // Security check: Only self, ADMIN, or HOD can list user achievements
        boolean isSelf = currentUser.getId().equals(userId);
        boolean isAdminOrHod = currentUser.getRole() != null && 
                (currentUser.getRole().getName().equalsIgnoreCase("ADMIN") || currentUser.getRole().getName().equalsIgnoreCase("HOD"));

        if (!isSelf && !isAdminOrHod) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
        
        // Security check: HOD can only view their own department's achievements
        boolean isAdmin = currentUser.getRole() != null && currentUser.getRole().getName().equalsIgnoreCase("ADMIN");
        boolean isOwnDeptHod = currentUser.getRole() != null && currentUser.getRole().getName().equalsIgnoreCase("HOD") && 
                currentUser.getDepartment() != null && currentUser.getDepartment().getId().equals(departmentId);

        if (!isAdmin && !isOwnDeptHod) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
}
