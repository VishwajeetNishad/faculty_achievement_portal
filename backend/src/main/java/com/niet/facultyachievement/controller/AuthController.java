package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.AuthResponse;
import com.niet.facultyachievement.dto.LoginRequest;
import com.niet.facultyachievement.dto.UserResponse;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.security.JwtTokenProvider;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            // Audit failed login attempt (NO password or credential recorded!)
            auditLogService.logAction(
                    AuditAction.LOGIN_FAILURE,
                    "AUTH",
                    null,
                    "Failed login attempt for user: " + loginRequest.getEmail(),
                    loginRequest.getEmail(),
                    clientIp
            );
            throw ex;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .or(() -> userRepository.findByEmployeeId(loginRequest.getEmail()))
                .orElseThrow(() -> new RuntimeException("User record not found"));

        String role = user.getRole() != null ? user.getRole().getName() : "FACULTY";
        Long departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        String token = tokenProvider.generateToken(authentication, user.getId(), role, departmentId);

        // Audit successful login event
        auditLogService.logAction(
                AuditAction.LOGIN_SUCCESS,
                "AUTH",
                user.getId(),
                "User signed in successfully: " + user.getEmail(),
                user,
                clientIp
        );

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(role)
                .build();

        return ResponseEntity.ok(authResponse);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
