package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.service.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Faculty Achievement Management.
 * Base Path: /api/achievements
 * 
 * Note: Until Spring Security / JWT (Step 8+) is implemented,
 * the userId is supplied explicitly via request query parameter for development testing.
 */
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @PostMapping
    public ResponseEntity<AchievementResponse> createAchievement(
            @RequestParam(name = "userId") Long userId,
            @Valid @RequestBody AchievementCreateRequest request) {
        AchievementResponse response = achievementService.createAchievement(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AchievementResponse> getAchievementById(@PathVariable("id") Long id) {
        AchievementResponse response = achievementService.getAchievementById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AchievementResponse>> getAchievementsByUser(@PathVariable("userId") Long userId) {
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
    public ResponseEntity<List<AchievementResponse>> getAchievementsByDepartment(@PathVariable("departmentId") Long departmentId) {
        List<AchievementResponse> responses = achievementService.getAchievementsByDepartment(departmentId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AchievementResponse> updateAchievement(
            @PathVariable("id") Long id,
            @RequestParam(name = "userId") Long userId,
            @Valid @RequestBody AchievementUpdateRequest request) {
        AchievementResponse response = achievementService.updateAchievement(id, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAchievement(
            @PathVariable("id") Long id,
            @RequestParam(name = "userId") Long userId) {
        achievementService.deleteAchievement(id, userId);
        return ResponseEntity.noContent().build();
    }
}
