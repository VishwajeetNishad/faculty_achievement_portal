package com.niet.facultyachievement.dto.report;

import lombok.*;

import java.util.List;

/**
 * The whole accreditation report, as one JSON document.
 *
 * <p>Only APPROVED achievements are counted anywhere in here. See
 * {@link NaacReportCoverage} for what was left out and why — the report states
 * that on its face rather than quietly presenting a partial picture as a total.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaacReportResponse {

    private String reportTitle;

    /** ISO timestamp. A submitted document has to say when it was produced. */
    private String generatedAt;
    private String generatedByName;

    /** Echo of the filters actually applied, so the printed page can state its own scope. */
    private String fromYear;
    private String toYear;

    /** Null when the report covers every department. */
    private String departmentFilterName;

    /**
     * Academic years present in the data, newest first.
     *
     * <p>Derived from the rows rather than from the frontend's fixed list of
     * years, so a value the dropdown does not know about still gets a column
     * instead of being silently dropped.
     */
    private List<String> academicYears;

    /** Department x year totals across every category. */
    private List<NaacCountRow> summary;

    private List<NaacSectionResponse> sections;

    private NaacReportCoverage coverage;

    /**
     * Shown as a footnote on the report. Says that metric references are
     * institution-configurable, so nobody mistakes a blank one for a bug.
     */
    private String metricRefNote;
}
