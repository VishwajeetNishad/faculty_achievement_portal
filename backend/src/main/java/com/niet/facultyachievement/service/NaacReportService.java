package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.report.NaacReportResponse;

/**
 * Builds the institution's accreditation research-output report (NAAC / NBA).
 *
 * <p>Only APPROVED achievements are ever counted. That rule lives in the JPQL of
 * {@code AchievementRepository.findApprovedForReport} as a literal, not here as a
 * parameter, so no caller of this service can widen it.
 */
public interface NaacReportService {

    /**
     * The whole report as a data structure, for the report page to render.
     *
     * @param fromYear     inclusive lower bound on academic year, or null
     * @param toYear       inclusive upper bound on academic year, or null
     * @param departmentId restrict to one department, or null for all
     * @param actorEmail   who asked — resolved from the JWT by the controller,
     *                     never accepted from a request body
     */
    NaacReportResponse generate(String fromYear, String toYear, Long departmentId, String actorEmail);

    /**
     * The same report rendered to a single CSV file, and audited.
     *
     * <p>Built from the output of {@link #generate}, not from a second pass over
     * the data, so the download and the printed page cannot disagree.
     */
    byte[] exportCsv(String fromYear, String toYear, Long departmentId, String actorEmail);
}
