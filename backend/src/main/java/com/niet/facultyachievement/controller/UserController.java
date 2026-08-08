package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.UserProfileUpdateRequest;
import com.niet.facultyachievement.dto.UserResponse;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * PUT /api/users/me — Faculty self-profile update.
     * User identity derived from JWT (no client-controlled user ID).
     * Only fullName, phone, designation are applied from the DTO.
     * Role, department, status, employeeId, email, passwordHash are NEVER modified.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request) {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        // Only update allowed fields — ignore everything else
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            user.setDesignation(request.getDesignation().trim());
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.fromEntity(saved));
    }

    /**
     * GET /api/users — Admin-only: full institutional faculty roster.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/users/{id} — Admin-only: view single faculty detail.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    /**
     * GET /api/users/department — HOD-only: faculty in HOD's own department.
     * Department derived from authenticated user's JWT, NOT from query parameter.
     */
    @GetMapping("/department")
    @PreAuthorize("hasRole('HOD')")
    public ResponseEntity<List<UserResponse>> getDepartmentFaculty(Authentication authentication) {
        String email = authentication.getName();
        User hodUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated HOD not found"));

        if (hodUser.getDepartment() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<UserResponse> users = userRepository.findByDepartmentId(hodUser.getDepartment().getId())
                .stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }
}
