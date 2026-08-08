package com.niet.facultyachievement.dto.dashboard;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicYearStatDTO {
    private String academicYear;
    private Long count;
}
