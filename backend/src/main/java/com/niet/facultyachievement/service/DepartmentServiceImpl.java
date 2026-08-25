package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.DepartmentRequest;
import com.niet.facultyachievement.dto.DepartmentResponse;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.exception.ConflictException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getDepartmentsWithUserCounts() {
        // One grouped query for every count, then matched up in memory. Asking
        // the database once per department would work too, but this keeps the
        // screen at two queries no matter how many departments exist.
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : userRepository.countUsersGroupedByDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }

        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(d -> DepartmentResponse.fromEntity(d, counts.getOrDefault(d.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request, String actorEmail) {
        User actor = loadActor(actorEmail);

        String code = request.getCode().trim().toUpperCase();
        if (departmentRepository.existsByCode(code)) {
            throw new ConflictException("A department with the code '" + code + "' already exists.");
        }

        Department department = Department.builder()
                .code(code)
                .name(request.getName().trim())
                .description(isBlank(request.getDescription()) ? null : request.getDescription().trim())
                .build();

        Department saved = departmentRepository.save(department);

        auditLogService.logAction(AuditAction.DEPARTMENT_CREATED, "DEPARTMENT", saved.getId(),
                "Created department " + saved.getCode() + " (" + saved.getName() + ")",
                actor, null);

        return DepartmentResponse.fromEntity(saved, 0L);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request, String actorEmail) {
        User actor = loadActor(actorEmail);
        Department department = loadDepartment(id);

        List<String> changes = new ArrayList<>();

        String code = request.getCode().trim().toUpperCase();
        if (!code.equalsIgnoreCase(department.getCode())) {
            if (departmentRepository.existsByCode(code)) {
                throw new ConflictException("A department with the code '" + code + "' already exists.");
            }
            // Renaming a code is allowed, and safe: users point at the
            // department's id, not its code, so nobody is detached by this.
            changes.add("code (" + department.getCode() + " to " + code + ")");
            department.setCode(code);
        }

        String name = request.getName().trim();
        if (!name.equals(department.getName())) {
            changes.add("name");
            department.setName(name);
        }

        String description = isBlank(request.getDescription()) ? null : request.getDescription().trim();
        boolean descriptionChanged = description == null
                ? department.getDescription() != null
                : !description.equals(department.getDescription());
        if (descriptionChanged) {
            changes.add("description");
            department.setDescription(description);
        }

        long userCount = userRepository.countByDepartmentId(id);

        if (changes.isEmpty()) {
            return DepartmentResponse.fromEntity(department, userCount);
        }

        Department saved = departmentRepository.save(department);

        auditLogService.logAction(AuditAction.DEPARTMENT_UPDATED, "DEPARTMENT", saved.getId(),
                "Updated department " + saved.getCode() + " — changed: " + String.join(", ", changes),
                actor, null);

        return DepartmentResponse.fromEntity(saved, userCount);
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id, String actorEmail) {
        User actor = loadActor(actorEmail);
        Department department = loadDepartment(id);

        // Every user must belong to a department (the column is NOT NULL), so
        // deleting one that still has members would either fail at the database
        // level or orphan those accounts. Refused here with a clear count so the
        // administrator knows exactly what to move first.
        long userCount = userRepository.countByDepartmentId(id);
        if (userCount > 0) {
            throw new ConflictException(
                    "Cannot delete " + department.getCode() + ": " + userCount
                            + (userCount == 1 ? " account still belongs" : " accounts still belong")
                            + " to it. Move them to another department first.");
        }

        String code = department.getCode();
        String name = department.getName();
        departmentRepository.delete(department);

        auditLogService.logAction(AuditAction.DEPARTMENT_DELETED, "DEPARTMENT", id,
                "Deleted department " + code + " (" + name + ")", actor, null);
    }

    private User loadActor(String actorEmail) {
        return userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private Department loadDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
