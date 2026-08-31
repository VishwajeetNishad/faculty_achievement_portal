package com.niet.facultyachievement.controller;

import com.niet.facultyachievement.dto.report.NaacReportResponse;
import com.niet.facultyachievement.security.Permissions;
import com.niet.facultyachievement.service.NaacReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Accreditation reporting (NAAC / NBA).
 *
 * <p>No {@code SecurityConfig} entry is needed for this path. {@code /api/reports/**}
 * matches no {@code requestMatchers} rule, so it falls through to
 * {@code .anyRequest().authenticated()} and the {@code @PreAuthorize} annotations
 * below do the narrowing — {@code @EnableMethodSecurity} is on. (This is the
 * opposite of {@code /api/audit-logs/**}, which needed a URL rule so that holders
 * of {@code VIEW_AUDIT_LOGS} could reach its controller at all.)
 *
 * <p>The actor always comes from the injected {@link Authentication}, i.e. from
 * the verified JWT. No endpoint here reads an identity from a parameter.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final NaacReportService naacReportService;

    /**
     * GET /api/reports/naac — the department-wise, year-wise research report.
     *
     * <p>Open to {@code ROLE_ADMIN} or to any account granted
     * {@code VIEW_REPORTS}, matching {@code /api/dashboard/admin} exactly rather
     * than inventing a second scoping rule. A Head of Department asked to prepare
     * the institution's accreditation file is the case that permission exists for;
     * a HOD without it gets 403 and sees only their own department's data through
     * the HOD portal, as before.
     *
     * <p>All three filters are optional. Omitting them reports every department
     * and every year on record.
     */
    @GetMapping("/naac")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('" + Permissions.VIEW_REPORTS + "')")
    public ResponseEntity<NaacReportResponse> getNaacReport(
            Authentication authentication,
            @RequestParam(required = false) String fromYear,
            @RequestParam(required = false) String toYear,
            @RequestParam(required = false) Long departmentId
    ) {
        return ResponseEntity.ok(naacReportService.generate(
                fromYear, toYear, departmentId, authentication.getName()));
    }

    /**
     * GET /api/reports/naac/export/csv — the same report as one CSV file.
     *
     * <p><strong>Requires {@code VIEW_REPORTS} *and* {@code EXPORT_REPORTS}.</strong>
     * The permissions screen grants the two independently, so "holds
     * {@code EXPORT_REPORTS} but not {@code VIEW_REPORTS}" is a state an
     * administrator can actually create by accident — and downloading a file you
     * are not allowed to open on screen is the wrong answer to that. One extra
     * clause closes it.
     *
     * <p>{@code ROLE_ADMIN} keeps its unconditional access, so nothing an
     * administrator could do before this endpoint existed has changed.
     *
     * <p>Exports are written to the audit trail; reads are not. Viewing keeps the
     * data inside the application, while a download puts every faculty member's
     * verified record into a file that travels.
     */
    @GetMapping("/naac/export/csv")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('" + Permissions.VIEW_REPORTS + "') "
            + "and hasAuthority('" + Permissions.EXPORT_REPORTS + "'))")
    public ResponseEntity<byte[]> exportNaacReportCsv(
            Authentication authentication,
            @RequestParam(required = false) String fromYear,
            @RequestParam(required = false) String toYear,
            @RequestParam(required = false) Long departmentId
    ) {
        byte[] csv = naacReportService.exportCsv(
                fromYear, toYear, departmentId, authentication.getName());

        String filename = "naac-research-report_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
