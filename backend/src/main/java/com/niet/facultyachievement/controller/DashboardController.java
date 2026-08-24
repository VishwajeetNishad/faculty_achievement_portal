package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.dashboard.AdminDashboardResponse;
import com.niet.facultyachievement.dto.dashboard.FacultyDashboardResponse;
import com.niet.facultyachievement.dto.dashboard.HodDashboardResponse;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/faculty — Authenticated user's dashboard analytics.
     * User identity derived strictly from Bearer JWT.
     */
    @GetMapping("/faculty")
    public ResponseEntity<FacultyDashboardResponse> getFacultyDashboard(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(dashboardService.getFacultyDashboard(email));
    }

    /**
     * GET /api/dashboard/hod — HOD's department dashboard analytics.
     * Department derived strictly from authenticated user's profile in JWT.
     */
    @GetMapping("/hod")
    @PreAuthorize("hasRole('HOD')")
    public ResponseEntity<HodDashboardResponse> getHodDashboard(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(dashboardService.getHodDashboard(email));
    }

    /**
     * GET /api/dashboard/admin — Institutional dashboard & department comparison analytics.
     *
     * <p>Accessible to ROLE_ADMIN, exactly as before, and additionally to any
     * account explicitly granted VIEW_REPORTS. This is an institution-wide
     * report, which is precisely what that permission is for — for example a
     * Head of Department asked to prepare a comparison across departments.
     *
     * <p>Purely additive: the original {@code hasRole('ADMIN')} clause is
     * untouched, so nothing an administrator could do before has changed.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.VIEW_REPORTS + "')")
    public ResponseEntity<AdminDashboardResponse> getAdminDashboard() {
        return ResponseEntity.ok(dashboardService.getAdminDashboard());
    }
}
