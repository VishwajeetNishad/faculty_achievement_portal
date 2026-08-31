package com.niet.facultyachievement.dto.report;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One department's row in a department x academic-year count matrix.
 *
 * <p>Used for both the report-wide summary and each section's own matrix, since
 * both are the same shape.
 *
 * <p>{@code countsByYear} is keyed by academic year and always carries an entry
 * for every year in the report — a zero is written explicitly rather than left
 * absent, because a missing cell in an accreditation table reads as an omission
 * while a printed 0 reads as a fact.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaacCountRow {

    private Long departmentId;
    private String departmentCode;
    private String departmentName;

    @Builder.Default
    private Map<String, Long> countsByYear = new LinkedHashMap<>();

    private long total;
}
