package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.NotificationResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.NotificationType;
import com.niet.facultyachievement.entity.User;

public interface NotificationService {

    PagedResponse<NotificationResponse> getUserNotifications(User user, int page, int size);

    long getUnreadCount(User user);

    NotificationResponse markAsRead(Long notificationId, User user);

    void markAllAsRead(User user);

    void createNotification(User recipient, String title, String message, NotificationType type, Achievement achievement);

    void notifyDepartmentHods(Long departmentId, String title, String message, NotificationType type, Achievement achievement);
}
