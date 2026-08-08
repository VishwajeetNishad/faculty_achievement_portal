package com.niet.facultyachievement.dto.dashboard;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardResponse {
    private long totalFaculty;
    private long activeFacultyCount;
    private long totalDepartments;
    private long totalAchievements;
    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
    private List<DepartmentStatResponse> departmentComparison;
    private Map<String, Long> categoryDistribution;
    private Map<String, Long> academicYearDistribution;
}
