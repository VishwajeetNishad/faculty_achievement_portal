package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.dashboard.AdminDashboardResponse;
import com.niet.facultyachievement.dto.dashboard.FacultyDashboardResponse;
import com.niet.facultyachievement.dto.dashboard.HodDashboardResponse;

public interface DashboardService {
    FacultyDashboardResponse getFacultyDashboard(String email);
    HodDashboardResponse getHodDashboard(String email);
    AdminDashboardResponse getAdminDashboard();
}
