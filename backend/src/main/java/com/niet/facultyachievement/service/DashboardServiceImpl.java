package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.AchievementResponse;
import com.niet.facultyachievement.dto.dashboard.*;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public FacultyDashboardResponse getFacultyDashboard(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Long userId = user.getId();

        long total = achievementRepository.countByUserId(userId);
        long pending = achievementRepository.countByUserIdAndStatus(userId, AchievementStatus.PENDING);
        long approved = achievementRepository.countByUserIdAndStatus(userId, AchievementStatus.APPROVED);
        long rejected = achievementRepository.countByUserIdAndStatus(userId, AchievementStatus.REJECTED);

        Map<String, Long> categoryDist = achievementRepository.getCategoryStatsByUserId(userId).stream()
                .collect(Collectors.toMap(
                        CategoryStatDTO::getCategoryName,
                        CategoryStatDTO::getCount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        Map<String, Long> yearDist = achievementRepository.getAcademicYearStatsByUserId(userId).stream()
                .collect(Collectors.toMap(
                        AcademicYearStatDTO::getAcademicYear,
                        AcademicYearStatDTO::getCount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        List<AchievementResponse> recent = achievementRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AchievementResponse::fromEntity)
                .collect(Collectors.toList());

        return FacultyDashboardResponse.builder()
                .totalAchievements(total)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .categoryDistribution(categoryDist)
                .academicYearDistribution(yearDist)
                .recentAchievements(recent)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HodDashboardResponse getHodDashboard(String email) {
        User hodUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated HOD not found"));

        Department dept = hodUser.getDepartment();
        if (dept == null) {
            throw new ResourceNotFoundException("HOD is not assigned to any department");
        }

        Long deptId = dept.getId();

        long facultyCount = userRepository.countByDepartmentId(deptId);
        long total = achievementRepository.countByUserDepartmentId(deptId);
        long pending = achievementRepository.countByUserDepartmentIdAndStatus(deptId, AchievementStatus.PENDING);
        long approved = achievementRepository.countByUserDepartmentIdAndStatus(deptId, AchievementStatus.APPROVED);
        long rejected = achievementRepository.countByUserDepartmentIdAndStatus(deptId, AchievementStatus.REJECTED);

        Map<String, Long> categoryDist = achievementRepository.getCategoryStatsByDepartmentId(deptId).stream()
                .collect(Collectors.toMap(
                        CategoryStatDTO::getCategoryName,
                        CategoryStatDTO::getCount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        Map<String, Long> yearDist = achievementRepository.getAcademicYearStatsByDepartmentId(deptId).stream()
                .collect(Collectors.toMap(
                        AcademicYearStatDTO::getAcademicYear,
                        AcademicYearStatDTO::getCount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        List<AchievementResponse> recent = achievementRepository.findTop5ByUserDepartmentIdOrderByCreatedAtDesc(deptId).stream()
                .map(AchievementResponse::fromEntity)
                .collect(Collectors.toList());

        return HodDashboardResponse.builder()
                .departmentId(dept.getId())
                .departmentCode(dept.getCode())
                .departmentName(dept.getName())
                .facultyCount(facultyCount)
                .totalAchievements(total)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .categoryDistribution(categoryDist)
                .academicYearDistribution(yearDist)
                .recentSubmissions(recent)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard() {
        long totalFaculty = userRepository.count();
        long activeFaculty = userRepository.countByStatus(UserStatus.ACTIVE);
        long totalDepartments = departmentRepository.count();
        long totalAchievements = achievementRepository.count();

        long pending = achievementRepository.countByStatus(AchievementStatus.PENDING);
        long approved = achievementRepository.countByStatus(AchievementStatus.APPROVED);
        long rejected = achievementRepository.countByStatus(AchievementStatus.REJECTED);

        Map<String, Long> categoryDist = achievementRepository.getOverallCategoryStats().stream()
                .collect(Collectors.toMap(
                        CategoryStatDTO::getCategoryName,
                        CategoryStatDTO::getCount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        Map<String, Long> yearDist = achievementRepository.getOverallAcademicYearStats().stream()
                .collect(Collectors.toMap(
                        AcademicYearStatDTO::getAcademicYear,
                        AcademicYearStatDTO::getCount,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        List<DepartmentStatResponse> deptComparison = departmentRepository.findAll().stream()
                .map(dept -> DepartmentStatResponse.builder()
                        .departmentId(dept.getId())
                        .departmentCode(dept.getCode())
                        .departmentName(dept.getName())
                        .facultyCount(userRepository.countByDepartmentId(dept.getId()))
                        .totalAchievements(achievementRepository.countByUserDepartmentId(dept.getId()))
                        .approvedCount(achievementRepository.countByUserDepartmentIdAndStatus(dept.getId(), AchievementStatus.APPROVED))
                        .pendingCount(achievementRepository.countByUserDepartmentIdAndStatus(dept.getId(), AchievementStatus.PENDING))
                        .rejectedCount(achievementRepository.countByUserDepartmentIdAndStatus(dept.getId(), AchievementStatus.REJECTED))
                        .build())
                .collect(Collectors.toList());

        return AdminDashboardResponse.builder()
                .totalFaculty(totalFaculty)
                .activeFacultyCount(activeFaculty)
                .totalDepartments(totalDepartments)
                .totalAchievements(totalAchievements)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .departmentComparison(deptComparison)
                .categoryDistribution(categoryDist)
                .academicYearDistribution(yearDist)
                .build();
    }
}
