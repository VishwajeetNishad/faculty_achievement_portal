package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.DepartmentRequest;
import com.niet.facultyachievement.dto.DepartmentResponse;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;

    /**
     * GET /api/departments — List all departments (for filter dropdowns).
     * Any authenticated user can access.
     *
     * <p>Deliberately left open and unchanged: filter dropdowns on the faculty,
     * HOD and admin pages all depend on it, and a department code is not
     * sensitive. Only the write operations below are permission-gated.
     */
    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        List<DepartmentResponse> departments = departmentRepository.findAll().stream()
                .map(DepartmentResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(departments);
    }

    /**
     * GET /api/departments/summary — the same list plus how many accounts belong
     * to each department.
     *
     * <p>A separate endpoint rather than an extra field on the list above,
     * because the count needs a second query that the dropdowns have no use for,
     * and because knowing the staffing of every department is management
     * information rather than a lookup table.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_DEPARTMENTS + "')")
    public ResponseEntity<List<DepartmentResponse>> getDepartmentSummary() {
        return ResponseEntity.ok(departmentService.getDepartmentsWithUserCounts());
    }

    /**
     * POST /api/departments — add a department.
     *
     * <p>Gated by MANAGE_DEPARTMENTS. A department is more than a label: it
     * decides which faculty a Head of Department can see and verify, so adding
     * or renaming one changes who has authority over whom.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_DEPARTMENTS + "')")
    public ResponseEntity<DepartmentResponse> createDepartment(
            Authentication authentication,
            @Valid @RequestBody DepartmentRequest request) {

        DepartmentResponse created = departmentService.createDepartment(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/departments/{id} — rename a department or change its description. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_DEPARTMENTS + "')")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody DepartmentRequest request) {

        DepartmentResponse updated = departmentService.updateDepartment(id, request, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/departments/{id} — remove an empty department.
     *
     * <p>Refused with 409 while any account still belongs to it. Every user must
     * have a department, so the accounts have to be moved first.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.MANAGE_DEPARTMENTS + "')")
    public ResponseEntity<Void> deleteDepartment(
            Authentication authentication,
            @PathVariable("id") Long id) {

        departmentService.deleteDepartment(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
