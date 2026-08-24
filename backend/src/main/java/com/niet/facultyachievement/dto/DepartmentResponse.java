package com.niet.facultyachievement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.niet.facultyachievement.entity.Department;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentResponse {
    private Long id;
    private String code;
    private String name;

    /**
     * Always populated. Added so the department management screen can show and
     * edit it; every other caller simply receives one extra JSON field, which
     * changes nothing for them.
     */
    private String description;

    /**
     * How many accounts belong to this department.
     *
     * <p>Only filled in by the management listing, which needs it to explain why
     * a department cannot be deleted. Left null on the plain
     * {@code GET /api/departments} used by filter dropdowns — counting there
     * would mean one extra query per department for information nobody reads.
     * Null is omitted from the JSON entirely, so that response is byte-for-byte
     * unchanged apart from the new description field.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long userCount;

    public static DepartmentResponse fromEntity(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .description(department.getDescription())
                .build();
    }

    /** Same as {@link #fromEntity(Department)} but also reports the user count. */
    public static DepartmentResponse fromEntity(Department department, Long userCount) {
        DepartmentResponse response = fromEntity(department);
        response.setUserCount(userCount == null ? 0L : userCount);
        return response;
    }
}
