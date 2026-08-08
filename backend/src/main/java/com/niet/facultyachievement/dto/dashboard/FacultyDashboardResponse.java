package com.niet.facultyachievement.dto.dashboard;

import com.niet.facultyachievement.dto.AchievementResponse;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacultyDashboardResponse {
    private long totalAchievements;
    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
    private Map<String, Long> categoryDistribution;
    private Map<String, Long> academicYearDistribution;
    private List<AchievementResponse> recentAchievements;
}
