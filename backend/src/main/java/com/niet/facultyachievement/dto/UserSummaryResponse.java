package com.niet.facultyachievement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryResponse {
    private Long id;
    private String employeeId;
    private String fullName;
    private String email;
    private String departmentCode;
    private String departmentName;
    private String designation;
    private String role;
}
