package com.niet.facultyachievement.dto;

import com.niet.facultyachievement.entity.Notification;
import com.niet.facultyachievement.entity.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private Long recipientId;
    private String recipientName;
    private String title;
    private String message;
    private NotificationType notificationType;
    private Long achievementId;
    private String achievementTitle;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse fromEntity(Notification notification) {
        if (notification == null) return null;
        var recipient = notification.getRecipient();
        var achievement = notification.getAchievement();

        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientId(recipient != null ? recipient.getId() : null)
                .recipientName(recipient != null ? recipient.getFullName() : null)
                .title(notification.getTitle())
                .message(notification.getMessage())
                .notificationType(notification.getNotificationType())
                .achievementId(achievement != null ? achievement.getId() : null)
                .achievementTitle(achievement != null ? achievement.getTitle() : null)
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
