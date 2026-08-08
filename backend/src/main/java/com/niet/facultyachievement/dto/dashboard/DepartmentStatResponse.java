package com.niet.facultyachievement.dto.dashboard;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentStatResponse {
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private long facultyCount;
    private long totalAchievements;
    private long approvedCount;
    private long pendingCount;
    private long rejectedCount;
}
