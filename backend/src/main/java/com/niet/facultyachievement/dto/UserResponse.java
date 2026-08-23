package com.niet.facultyachievement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.niet.facultyachievement.entity.User;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String employeeId;
    private String fullName;
    private String email;
    private String designation;
    private String phone;
    private String departmentCode;
    private String departmentName;
    private Long departmentId;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * The permission codes this user holds. Only populated by
     * {@code GET /api/auth/me}, so the frontend can hide buttons the user
     * cannot use. Left null everywhere else, in which case it is omitted from
     * the JSON entirely.
     *
     * <p>This is a convenience for the interface only. The browser can freely
     * edit this list, so it must never be treated as an authorisation decision
     * — the backend re-checks every permission on every request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> permissions;

    /**
     * Safe factory method — NEVER includes passwordHash.
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .designation(user.getDesignation())
                .phone(user.getPhone())
                .departmentCode(user.getDepartment() != null ? user.getDepartment().getCode() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .role(user.getRole() != null ? user.getRole().getName() : "FACULTY")
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Same as {@link #fromEntity(User)} but also reports the user's permissions.
     *
     * <p>A separate overload rather than a change to the method above, so the
     * many existing callers (admin roster, HOD department list, profile update)
     * keep returning exactly the same JSON as before. Only
     * {@code GET /api/auth/me} uses this version.
     */
    public static UserResponse fromEntity(User user, Collection<String> permissionCodes) {
        UserResponse response = fromEntity(user);
        response.setPermissions(permissionCodes == null ? List.of() : List.copyOf(permissionCodes));
        return response;
    }
}
