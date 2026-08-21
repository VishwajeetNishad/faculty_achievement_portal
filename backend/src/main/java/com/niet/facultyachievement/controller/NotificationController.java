package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.NotificationResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for In-App Notifications.
 * Base Endpoint: /api/notifications
 *
 * All endpoints rely strictly on JWT / SecurityContextHolder to identify the requesting user.
 * Users can only retrieve, view, and mark as read their own notifications (IDOR / BOLA protected).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("User is not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user record not found"));
    }

    /**
     * GET /api/notifications
     * Retrieve paginated notifications for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> getMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User currentUser = getAuthenticatedUser(authentication);
        PagedResponse<NotificationResponse> response = notificationService.getUserNotifications(currentUser, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/notifications/unread-count
     * Efficient unread notification count query.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        long count = notificationService.getUnreadCount(currentUser);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * PATCH /api/notifications/{id}/read
     * Mark a specific notification as read. Enforces IDOR security.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markNotificationAsRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = getAuthenticatedUser(authentication);
        NotificationResponse response = notificationService.markAsRead(id, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/notifications/read-all
     * Mark all unread notifications for the authenticated user as read.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllNotificationsAsRead(Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        notificationService.markAllAsRead(currentUser);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
