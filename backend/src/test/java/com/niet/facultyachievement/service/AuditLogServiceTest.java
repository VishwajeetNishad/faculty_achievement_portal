package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AuditLogResponse;
import com.niet.facultyachievement.dto.PagedResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.AuditLog;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private User sampleUser;
    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder().id(1L).fullName("Admin User").email("admin@niet.co.in").build();

        sampleLog = AuditLog.builder()
                .id(100L)
                .actor(sampleUser)
                .actorEmail("admin@niet.co.in")
                .action(AuditAction.LOGIN_SUCCESS)
                .entityType("AUTH")
                .entityId(1L)
                .description("User logged in successfully")
                .ipAddress("127.0.0.1")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void logAction_AuthenticatedUser_Success() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleLog);

        auditLogService.logAction(AuditAction.LOGIN_SUCCESS, "AUTH", 1L, "Test log", sampleUser, "127.0.0.1");

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void searchAuditLogs_Success() {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLog));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<AuditLogResponse> result = auditLogService.searchAuditLogs(
                AuditAction.LOGIN_SUCCESS, "AUTH", 1L, null, null, 0, 10, "createdAt", "desc"
        );

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("AUTH", result.getContent().get(0).getEntityType());
    }

    @Test
    void searchAuditLogs_InvalidSortField_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                auditLogService.searchAuditLogs(null, null, null, null, null, 0, 10, "passwordHash", "desc")
        );
    }
}
