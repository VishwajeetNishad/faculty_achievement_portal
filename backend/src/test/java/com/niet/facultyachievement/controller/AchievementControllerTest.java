package com.niet.facultyachievement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.niet.facultyachievement.dto.AchievementCreateRequest;
import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.AchievementUpdateRequest;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.Role;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.GlobalExceptionHandler;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.service.AchievementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AchievementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AchievementService achievementService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AchievementController achievementController;

    private ObjectMapper objectMapper;
    private AchievementResponse sampleResponse;
    private User mockUser;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(achievementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Role adminRole = Role.builder().id(1L).name("ADMIN").description("Admin").build();
        Department cseDept = Department.builder().id(1L).code("CSE").name("Computer Science & Engineering").build();

        mockUser = User.builder()
                .id(1L)
                .employeeId("EMP001")
                .fullName("Dr. Sharma")
                .email("admin@faculty.edu")
                .role(adminRole)
                .department(cseDept)
                .build();

        mockAuth = new UsernamePasswordAuthenticationToken("admin@faculty.edu", null, List.of());

        sampleResponse = AchievementResponse.builder()
                .id(1L)
                .userId(1L)
                .facultyName("Dr. Sharma")
                .facultyEmail("admin@faculty.edu")
                .employeeId("EMP001")
                .departmentCode("CSE")
                .departmentName("Computer Science & Engineering")
                .categoryId(1L)
                .categoryCode("PUBLICATION")
                .categoryName("Research Publication")
                .title("Deep Learning in Healthcare")
                .description("Published paper")
                .achievementDate(LocalDate.of(2025, 5, 10))
                .academicYear("2025-2026")
                .status(AchievementStatus.PENDING)
                .build();
    }

    @Test
    void createAchievement_Success_Returns201() throws Exception {
        AchievementCreateRequest request = AchievementCreateRequest.builder()
                .categoryId(1L)
                .title("Deep Learning in Healthcare")
                .achievementDate(LocalDate.of(2025, 5, 10))
                .academicYear("2025-2026")
                .build();

        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        when(achievementService.createAchievement(eq(1L), any(AchievementCreateRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/achievements")
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Deep Learning in Healthcare"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createAchievement_ValidationError_Returns400() throws Exception {
        AchievementCreateRequest invalidRequest = AchievementCreateRequest.builder()
                .categoryId(null) // Violation: @NotNull
                .title("")        // Violation: @NotBlank
                .build();

        mockMvc.perform(post("/api/achievements")
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void getAchievementById_Success_Returns200() throws Exception {
        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        when(achievementService.getAchievementById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/achievements/1").principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Deep Learning in Healthcare"));
    }

    @Test
    void getAchievementById_NotFound_Returns404() throws Exception {
        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        when(achievementService.getAchievementById(99L))
                .thenThrow(new ResourceNotFoundException("Achievement not found with id: 99"));

        mockMvc.perform(get("/api/achievements/99").principal(mockAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Achievement not found with id: 99"));
    }

    @Test
    void getAchievementsByUser_Returns200() throws Exception {
        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        when(achievementService.getAchievementsByUser(1L))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/achievements/user/1").principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void getAchievementsByStatus_Valid_Returns200() throws Exception {
        when(achievementService.getAchievementsByStatus(AchievementStatus.PENDING))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/achievements/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAchievementsByStatus_Invalid_Returns400() throws Exception {
        mockMvc.perform(get("/api/achievements/status/INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid achievement status: INVALID_STATUS. Allowed values: PENDING, APPROVED, REJECTED."));
    }

    @Test
    void getAchievementsByDepartment_Returns200() throws Exception {
        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        when(achievementService.getAchievementsByDepartment(1L))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/achievements/department/1").principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].departmentCode").value("CSE"));
    }

    @Test
    void updateAchievement_Success_Returns200() throws Exception {
        AchievementUpdateRequest updateRequest = AchievementUpdateRequest.builder()
                .categoryId(1L)
                .title("Updated Title")
                .achievementDate(LocalDate.of(2025, 5, 10))
                .academicYear("2025-2026")
                .build();

        sampleResponse.setTitle("Updated Title");

        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        when(achievementService.updateAchievement(eq(1L), eq(1L), any(AchievementUpdateRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/achievements/1")
                        .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void deleteAchievement_Success_Returns204() throws Exception {
        when(userRepository.findByEmail("admin@faculty.edu")).thenReturn(Optional.of(mockUser));
        doNothing().when(achievementService).deleteAchievement(1L, 1L);

        mockMvc.perform(delete("/api/achievements/1").principal(mockAuth))
                .andExpect(status().isNoContent());

        verify(achievementService, times(1)).deleteAchievement(1L, 1L);
    }
}
