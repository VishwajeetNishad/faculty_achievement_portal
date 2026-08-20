package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.NotificationResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.Notification;
import com.niet.facultyachievement.entity.NotificationType;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.NotificationRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUserNotifications(User user, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> resultPage = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId(), pageable);
        return PagedResponse.from(resultPage, NotificationResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(user.getId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        // IDOR Check: Users can only mark their own notifications as read
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not authorized to access or modify this notification");
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.fromEntity(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(User user) {
        notificationRepository.markAllAsReadForUser(user.getId());
    }

    @Override
    @Transactional
    public void createNotification(User recipient, String title, String message, NotificationType type, Achievement achievement) {
        if (recipient == null) return;

        Notification notification = Notification.builder()
                .recipient(recipient)
                .title(title)
                .message(message)
                .notificationType(type)
                .achievement(achievement)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void notifyDepartmentHods(Long departmentId, String title, String message, NotificationType type, Achievement achievement) {
        if (departmentId == null) return;

        List<User> deptUsers = userRepository.findByDepartmentId(departmentId);
        List<User> hods = deptUsers.stream()
                .filter(u -> u.getRole() != null &&
                        (u.getRole().getName().equalsIgnoreCase("HOD") ||
                         u.getRole().getName().equalsIgnoreCase("ROLE_HOD")))
                .collect(Collectors.toList());

        for (User hod : hods) {
            createNotification(hod, title, message, type, achievement);
        }
    }
}
