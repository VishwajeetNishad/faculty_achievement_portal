package com.niet.facultyachievement.security;

import com.niet.facultyachievement.dto.UserResponse;
import com.niet.facultyachievement.dto.UserStatusUpdateRequest;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.Role;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ConflictException;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.PermissionRepository;
import com.niet.facultyachievement.repository.RoleRepository;
import com.niet.facultyachievement.repository.UserPermissionRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.AuditLogService;
import com.niet.facultyachievement.service.PermissionServiceImpl;
import com.niet.facultyachievement.service.PublicSlugGenerator;
import com.niet.facultyachievement.service.UserManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Track A — the escalation guards that decide who may hand out authority.
 *
 * <p>Pure Mockito, no Spring context and no database: every rule under test
 * lives in a service method, so it can be proven by calling that method with
 * mocked collaborators. This mirrors the existing {@code AchievementServiceTest}
 * style and needs no running MySQL.
 *
 * <p>Two services are exercised here because the escalation rules span both:
 * {@link PermissionServiceImpl} (the five grant guards) and
 * {@link UserManagementServiceImpl} (the last-active-administrator guard).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Track A — permission-grant and account-status security guards")
class PermissionSecurityTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private UserPermissionRepository userPermissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPermissionResolver userPermissionResolver;
    @Mock private AuditLogService auditLogService;

    // Extra collaborators that only UserManagementServiceImpl's constructor needs.
    @Mock private RoleRepository roleRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PublicSlugGenerator publicSlugGenerator;

    @InjectMocks private PermissionServiceImpl permissionService;
    @InjectMocks private UserManagementServiceImpl userManagementService;

    private Role adminRole;
    private Role hodRole;
    private Role facultyRole;
    private Department department;

    private User admin;         // id 1, ROLE_ADMIN, ACTIVE — performs the actions
    private User hodWithGrants;  // id 2, ROLE_HOD — a delegated manager
    private User faculty;        // id 3, ROLE_FACULTY — a normal target
    private User secondAdmin;    // id 4, ROLE_ADMIN, ACTIVE — the account acted upon

    private static final String ADMIN_EMAIL = "admin@niet.co.in";
    private static final String HOD_EMAIL = "hod@niet.co.in";

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().id(3L).name("ROLE_ADMIN").build();
        hodRole = Role.builder().id(2L).name("ROLE_HOD").build();
        facultyRole = Role.builder().id(1L).name("ROLE_FACULTY").build();
        department = Department.builder().id(1L).code("CSE")
                .name("Computer Science & Engineering").build();

        admin = User.builder().id(1L).employeeId("EMP-A1").fullName("Dr. Admin")
                .email(ADMIN_EMAIL).designation("Director").department(department)
                .role(adminRole).status(UserStatus.ACTIVE).build();

        hodWithGrants = User.builder().id(2L).employeeId("EMP-H1").fullName("Dr. HOD")
                .email(HOD_EMAIL).designation("Head of Department").department(department)
                .role(hodRole).status(UserStatus.ACTIVE).build();

        faculty = User.builder().id(3L).employeeId("EMP-F1").fullName("Dr. Faculty")
                .email("faculty@niet.co.in").designation("Assistant Professor").department(department)
                .role(facultyRole).status(UserStatus.ACTIVE).build();

        secondAdmin = User.builder().id(4L).employeeId("EMP-A2").fullName("Dr. Second Admin")
                .email("admin2@niet.co.in").designation("Dean").department(department)
                .role(adminRole).status(UserStatus.ACTIVE).build();
    }

    // ---------------------------------------------------------------- Guard 1

    @Test
    @DisplayName("Guard 1: an actor cannot change their own permissions (403)")
    void cannotEditOwnPermissions() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(AccessDeniedException.class, () ->
                permissionService.updateUserPermissions(1L, List.of("CREATE_FACULTY"), ADMIN_EMAIL));

        verify(userPermissionRepository, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------- Guard 2

    @Test
    @DisplayName("Guard 2: individual permissions cannot be assigned to an administrator (400)")
    void cannotAssignPermissionsToAdmin() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userRepository.findById(4L)).thenReturn(Optional.of(secondAdmin));
        when(userPermissionResolver.isAdmin(secondAdmin)).thenReturn(true);

        assertThrows(BadRequestException.class, () ->
                permissionService.updateUserPermissions(4L, List.of("CREATE_FACULTY"), ADMIN_EMAIL));
    }

    // ---------------------------------------------------------------- Guard 3

    @Test
    @DisplayName("Guard 3: an unknown permission code is rejected, not silently dropped (400)")
    void rejectsUnknownPermissionCode() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userRepository.findById(3L)).thenReturn(Optional.of(faculty));
        when(userPermissionResolver.isAdmin(faculty)).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                permissionService.updateUserPermissions(3L, List.of("MAKE_ME_KING"), ADMIN_EMAIL));

        verify(userPermissionRepository, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------- Guard 4

    @Test
    @DisplayName("Guard 4: a non-admin cannot grant the admin-only CREATE_ADMIN (403)")
    void nonAdminCannotGrantAdminOnlyPermission() {
        // A HOD who has somehow been given MANAGE_PERMISSIONS and CREATE_ADMIN
        // still may not pass CREATE_ADMIN on, because they are not an admin.
        when(userRepository.findByEmail(HOD_EMAIL)).thenReturn(Optional.of(hodWithGrants));
        when(userRepository.findById(3L)).thenReturn(Optional.of(faculty));
        when(userPermissionResolver.isAdmin(faculty)).thenReturn(false);
        when(userPermissionRepository.findPermissionCodesByUserId(3L)).thenReturn(List.of());
        when(userPermissionResolver.isAdmin(hodWithGrants)).thenReturn(false);
        when(userPermissionResolver.resolvePermissionCodes(hodWithGrants))
                .thenReturn(Set.of(Permissions.MANAGE_PERMISSIONS, Permissions.CREATE_ADMIN));

        assertThrows(AccessDeniedException.class, () ->
                permissionService.updateUserPermissions(3L, List.of("CREATE_ADMIN"), HOD_EMAIL));

        verify(userPermissionRepository, never()).saveAll(anyList());
    }

    // ---------------------------------------------------------------- Guard 5

    @Test
    @DisplayName("Guard 5: an actor cannot grant a permission they do not hold themselves (403)")
    void cannotGrantPermissionActorDoesNotHold() {
        // The HOD holds MANAGE_PERMISSIONS but NOT CREATE_HOD, so cannot grant it.
        when(userRepository.findByEmail(HOD_EMAIL)).thenReturn(Optional.of(hodWithGrants));
        when(userRepository.findById(3L)).thenReturn(Optional.of(faculty));
        when(userPermissionResolver.isAdmin(faculty)).thenReturn(false);
        when(userPermissionRepository.findPermissionCodesByUserId(3L)).thenReturn(List.of());
        when(userPermissionResolver.isAdmin(hodWithGrants)).thenReturn(false);
        when(userPermissionResolver.resolvePermissionCodes(hodWithGrants))
                .thenReturn(Set.of(Permissions.MANAGE_PERMISSIONS));

        assertThrows(AccessDeniedException.class, () ->
                permissionService.updateUserPermissions(3L, List.of("CREATE_HOD"), HOD_EMAIL));
    }

    // -------------------------------------------------- Last-active-admin guard

    @Test
    @DisplayName("Last-admin guard: deactivating the only active administrator is refused (409)")
    void cannotDeactivateLastActiveAdmin() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userRepository.findById(4L)).thenReturn(Optional.of(secondAdmin));
        when(userPermissionResolver.resolvePermissionCodes(admin)).thenReturn(Permissions.ALL);
        when(userPermissionResolver.isAdmin(secondAdmin)).thenReturn(true);
        when(userPermissionResolver.isAdmin(admin)).thenReturn(true);
        when(userRepository.countByRoleAndStatusExcludingUser(anyLong(), eq(UserStatus.ACTIVE), eq(4L)))
                .thenReturn(0L);

        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder()
                .status("INACTIVE").reason("stepping down").build();

        assertThrows(ConflictException.class, () ->
                userManagementService.updateUserStatus(4L, request, ADMIN_EMAIL));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Last-admin guard: an admin can be deactivated while another active admin remains (200)")
    void canDeactivateAdminWhenAnotherRemains() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(userRepository.findById(4L)).thenReturn(Optional.of(secondAdmin));
        when(userPermissionResolver.resolvePermissionCodes(admin)).thenReturn(Permissions.ALL);
        when(userPermissionResolver.isAdmin(secondAdmin)).thenReturn(true);
        when(userPermissionResolver.isAdmin(admin)).thenReturn(true);
        when(userRepository.countByRoleAndStatusExcludingUser(anyLong(), eq(UserStatus.ACTIVE), eq(4L)))
                .thenReturn(1L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserStatusUpdateRequest request = UserStatusUpdateRequest.builder().status("INACTIVE").build();

        UserResponse response = userManagementService.updateUserStatus(4L, request, ADMIN_EMAIL);

        assertNotNull(response);
        assertEquals(UserStatus.INACTIVE.name(), response.getStatus());
        assertEquals("admin2@niet.co.in", response.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }
}
