package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.DepartmentRequest;
import com.niet.facultyachievement.dto.DepartmentResponse;

import java.util.List;

/**
 * Managing the list of departments.
 *
 * <p>Reading the list stays open to every signed-in user — the filter dropdowns
 * throughout the portal depend on it. Changing the list is gated by
 * MANAGE_DEPARTMENTS, because a department is not just a label: it decides which
 * faculty a Head of Department can see and verify.
 */
public interface DepartmentService {

    /** The list plus a user count per department, for the management screen. */
    List<DepartmentResponse> getDepartmentsWithUserCounts();

    DepartmentResponse createDepartment(DepartmentRequest request, String actorEmail);

    DepartmentResponse updateDepartment(Long id, DepartmentRequest request, String actorEmail);

    void deleteDepartment(Long id, String actorEmail);
}
