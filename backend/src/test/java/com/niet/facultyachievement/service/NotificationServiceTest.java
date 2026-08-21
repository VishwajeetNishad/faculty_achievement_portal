package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.NotificationResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.Notification;
import com.niet.facultyachievement.entity.NotificationType;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.repository.NotificationRepository;
import com.niet.facultyachievement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User sampleUser;
    private User otherUser;
    private Notification sampleNotification;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder().id(1L).fullName("Faculty One").email("fac1@niet.co.in").build();
        otherUser = User.builder().id(2L).fullName("Faculty Two").email("fac2@niet.co.in").build();

        sampleNotification = Notification.builder()
                .id(10L)
                .recipient(sampleUser)
                .title("Test Title")
                .message("Test Message")
                .notificationType(NotificationType.ACHIEVEMENT_SUBMITTED)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getUserNotifications_Success() {
        Page<Notification> page = new PageImpl<>(List.of(sampleNotification));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class))).thenReturn(page);

        PagedResponse<NotificationResponse> result = notificationService.getUserNotifications(sampleUser, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Title", result.getContent().get(0).getTitle());
    }

    @Test
    void getUnreadCount_Success() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(1L)).thenReturn(5L);

        long count = notificationService.getUnreadCount(sampleUser);

        assertEquals(5L, count);
    }

    @Test
    void markAsRead_Success() {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(sampleNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(sampleNotification);

        NotificationResponse response = notificationService.markAsRead(10L, sampleUser);

        assertNotNull(response);
        verify(notificationRepository, times(1)).save(sampleNotification);
    }

    @Test
    void markAsRead_IDOR_AccessDenied() {
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(sampleNotification));

        assertThrows(AccessDeniedException.class, () -> notificationService.markAsRead(10L, otherUser));
    }
}
