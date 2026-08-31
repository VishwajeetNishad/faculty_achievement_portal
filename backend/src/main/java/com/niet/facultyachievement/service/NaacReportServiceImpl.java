package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.report.*;
import com.niet.facultyachievement.entity.*;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.UserRepository;
import com.niet.facultyachievement.util.Csv;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Turns the portal's verified achievement records into the department-wise,
 * year-wise research output report an accreditation body asks for.
 *
 * <p>Everything is aggregated in Java from one fetch rather than from a query per
 * section. Six sections x N departments x M years would otherwise be dozens of
 * round trips to draw one page.
 */
@Service
@RequiredArgsConstructor
public class NaacReportServiceImpl implements NaacReportService {

    /**
     * Same cap as the existing achievement CSV export
     * ({@code AchievementServiceImpl:542}). The difference is what happens when
     * it bites: that export truncates silently, this one sets
     * {@code coverage.rowCapReached} and the page and the CSV both say so.
     */
    static final int ROW_CAP = 5000;

    /**
     * Printed where a real enum value is absent from a record.
     *
     * <p>Spelled out rather than left blank so a reader can tell "nobody filled
     * this in" apart from a rendering fault.
     */
    private static final String NOT_SPECIFIED = "(not specified)";

    private static final String REPORT_TITLE =
            "Research Output Report for Accreditation (NAAC / NBA)";

    /**
     * Shown as a footnote wherever the report is rendered. Written out in full so
     * nobody has to read the source to understand why a metric column is blank.
     */
    private static final String METRIC_REF_NOTE =
            "Metric references are intentionally blank. NAAC publishes separate assessment "
            + "manuals for Universities, Autonomous Colleges and Affiliated/Constituent Colleges, "
            + "with different weightages across the seven criteria, so the correct metric "
            + "numbering depends on which manual applies to this institution. The accreditation "
            + "coordinator should fill these in from the institution's current manual "
            + "(NaacSection.java). A blank reference prints nothing; a wrong one would look "
            + "authoritative.";

    private final AchievementRepository achievementRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------
    // Report
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public NaacReportResponse generate(String fromYear, String toYear,
                                       Long departmentId, String actorEmail) {
        String from = blankToNull(fromYear);
        String to = blankToNull(toYear);
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new BadRequestException("fromYear must not be after toYear");
        }

        User actor = loadActor(actorEmail);

        // Resolved up front so an unknown id is a clean 404 rather than a report
        // that silently covers nothing.
        Department onlyDepartment = null;
        if (departmentId != null) {
            onlyDepartment = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department not found with id: " + departmentId));
        }

        List<Achievement> rows = achievementRepository.findApprovedForReport(
                departmentId, from, to, PageRequest.of(0, ROW_CAP));
        long approvedTotal = achievementRepository.countApprovedForReport(departmentId, from, to);

        Map<AchievementStatus, Long> excluded = new EnumMap<>(AchievementStatus.class);
        for (Object[] row : achievementRepository.countNonApprovedForReport(departmentId, from, to)) {
            excluded.put((AchievementStatus) row[0], (Long) row[1]);
        }

        // Departments come from the department table, not from the rows. A
        // department with no approved research still gets a line of zeros: in an
        // accreditation table an explicit zero is a fact, while a missing row
        // reads as something left out.
        List<Department> departments = onlyDepartment != null
                ? List.of(onlyDepartment)
                : departmentRepository.findAll().stream()
                        .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

        List<String> academicYears = deriveAcademicYears(rows);

        // Split the rows once; every section then reads its own bucket.
        Map<NaacSection, List<Achievement>> bySection = new EnumMap<>(NaacSection.class);
        for (NaacSection section : NaacSection.values()) {
            bySection.put(section, new ArrayList<>());
        }
        long unclassified = 0;
        for (Achievement a : rows) {
            NaacSection section = sectionOf(a);
            if (section == null) {
                unclassified++;
                continue;
            }
            bySection.get(section).add(a);
        }

        List<NaacSectionResponse> sections = new ArrayList<>();
        for (NaacSection section : NaacSection.values()) {
            sections.add(buildSection(section, bySection.get(section), departments, academicYears));
        }

        NaacReportCoverage coverage = NaacReportCoverage.builder()
                .approvedIncluded(rows.size())
                .pendingExcluded(excluded.getOrDefault(AchievementStatus.PENDING, 0L))
                .rejectedExcluded(excluded.getOrDefault(AchievementStatus.REJECTED, 0L))
                .unclassifiedExcluded(unclassified)
                .rowCapReached(approvedTotal > rows.size())
                .rowCap(ROW_CAP)
                .build();

        return NaacReportResponse.builder()
                .reportTitle(REPORT_TITLE)
                .generatedAt(LocalDateTime.now().withNano(0).toString())
                .generatedByName(actor.getFullName())
                .fromYear(from)
                .toYear(to)
                .departmentFilterName(onlyDepartment != null ? onlyDepartment.getName() : null)
                .academicYears(academicYears)
                .summary(buildCountRows(rows, departments, academicYears))
                .sections(sections)
                .coverage(coverage)
                .metricRefNote(METRIC_REF_NOTE)
                .build();
    }

    /**
     * Academic years actually present in the data, newest first.
     *
     * <p>Not taken from the four options the submission form offers, because
     * {@code AchievementCreateRequest.academicYear} carries no {@code @Pattern} —
     * an API client can store {@code 2024-25}. Deriving the columns means such a
     * value gets a column of its own instead of disappearing from the totals.
     */
    private List<String> deriveAcademicYears(List<Achievement> rows) {
        Set<String> years = new HashSet<>();
        for (Achievement a : rows) {
            if (a.getAcademicYear() != null && !a.getAcademicYear().isBlank()) {
                years.add(a.getAcademicYear());
            }
        }
        List<String> ordered = new ArrayList<>(years);
        ordered.sort(Comparator.reverseOrder());
        return ordered;
    }

    /**
     * Which section a record belongs in.
     *
     * <p>Publications split three ways on {@code PublicationType}: journals in
     * their own section, books, chapters and conference papers in the second,
     * and publications whose detail row was never created in the third. That
     * third case is not a technicality — {@code publications.publication_type}
     * is {@code NOT NULL}, so a null type means the whole detail row is absent
     * and <em>no</em> publication metadata exists for that record. Filing it
     * under "Books, Chapters & Conference Proceedings" would put a claim in a
     * submitted document that nobody entered, so it gets a heading that is true
     * instead.
     *
     * <p>Records in the other four categories stay in their own section when
     * their detail row is missing, because those headings do not assert a
     * sub-type: a patent is a patent whether or not its status was recorded.
     * Their unfilled columns simply print empty.
     *
     * <p>Returns null for a category code this report has no section for. The
     * caller counts those into {@code coverage.unclassifiedExcluded} instead of
     * ignoring them.
     */
    private NaacSection sectionOf(Achievement a) {
        String code = a.getCategory() != null ? a.getCategory().getCode() : null;
        if (code == null) return null;
        switch (code) {
            case "RESEARCH_GRANT":
                return NaacSection.RESEARCH_GRANTS;
            case "PATENT":
                return NaacSection.PATENTS;
            case "WORKSHOP_FDP":
                return NaacSection.WORKSHOPS_FDP;
            case "AWARD":
                return NaacSection.AWARDS;
            case "PUBLICATION":
                Publication p = a.getPublication();
                if (p == null || p.getPublicationType() == null) {
                    return NaacSection.PUBLICATIONS_UNCLASSIFIED;
                }
                return p.getPublicationType() == PublicationType.JOURNAL
                        ? NaacSection.PUBLICATIONS_JOURNAL
                        : NaacSection.PUBLICATIONS_OTHER;
            default:
                return null;
        }
    }

    private NaacSectionResponse buildSection(NaacSection section, List<Achievement> rows,
                                             List<Department> departments, List<String> academicYears) {
        List<List<String>> cells = new ArrayList<>(rows.size());
        Map<String, Long> breakdown = new LinkedHashMap<>();
        BigDecimal totalAmount = section == NaacSection.RESEARCH_GRANTS ? BigDecimal.ZERO : null;

        for (Achievement a : rows) {
            List<String> row = cellsFor(section, a);
            if (row.size() != section.getColumns().size()) {
                // A row that does not line up with its own header would print
                // one field's value under another field's name. Fail loudly here
                // rather than let a misaligned table reach a submitted document.
                throw new IllegalStateException("Report section " + section.name() + " built "
                        + row.size() + " cells for " + section.getColumns().size() + " columns");
            }
            cells.add(row);

            // A section with no breakdown label has no meaningful split to make —
            // every row in it carries the same (missing) value, so a one-bucket
            // table would restate the section title and nothing more.
            if (!section.getBreakdownLabel().isEmpty()) {
                String key = breakdownKeyFor(section, a);
                breakdown.merge(key, 1L, Long::sum);
            }

            if (totalAmount != null && a.getResearchGrant() != null
                    && a.getResearchGrant().getGrantAmount() != null) {
                // BigDecimal, never double: money must not accumulate float drift.
                totalAmount = totalAmount.add(a.getResearchGrant().getGrantAmount());
            }
        }

        // Sorted so the same data always prints in the same order — a report
        // whose rows shuffle between two runs is hard to diff and hard to trust.
        Map<String, Long> orderedBreakdown = new LinkedHashMap<>();
        breakdown.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> orderedBreakdown.put(e.getKey(), e.getValue()));

        return NaacSectionResponse.builder()
                .sectionKey(section.name())
                .title(section.getTitle())
                .subtitle(section.getSubtitle())
                .metricRef(section.getMetricRef())
                .columns(section.getColumns())
                .countsByDepartmentYear(buildCountRows(rows, departments, academicYears))
                .breakdown(orderedBreakdown)
                .breakdownLabel(section.getBreakdownLabel())
                .totalAmount(totalAmount)
                .rows(cells)
                .total(rows.size())
                .build();
    }

    /**
     * A department x academic-year count matrix over the given rows.
     *
     * <p>Used for both the report summary and each section, since both are the
     * same shape. Every department gets a row and every year gets a cell, zeros
     * written explicitly.
     */
    private List<NaacCountRow> buildCountRows(List<Achievement> rows,
                                              List<Department> departments,
                                              List<String> academicYears) {
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Achievement a : rows) {
            Department d = a.getUser() != null ? a.getUser().getDepartment() : null;
            if (d == null || a.getAcademicYear() == null) continue;
            counts.computeIfAbsent(d.getId(), k -> new HashMap<>())
                    .merge(a.getAcademicYear(), 1L, Long::sum);
        }

        List<NaacCountRow> result = new ArrayList<>(departments.size());
        for (Department d : departments) {
            Map<String, Long> perYear = counts.getOrDefault(d.getId(), Map.of());
            Map<String, Long> ordered = new LinkedHashMap<>();
            long total = 0;
            for (String year : academicYears) {
                long n = perYear.getOrDefault(year, 0L);
                ordered.put(year, n);
                total += n;
            }
            result.add(NaacCountRow.builder()
                    .departmentId(d.getId())
                    .departmentCode(d.getCode())
                    .departmentName(d.getName())
                    .countsByYear(ordered)
                    .total(total)
                    .build());
        }
        return result;
    }

    /**
     * The enum value this record contributes to its section's breakdown.
     *
     * <p>Every one of these is a real stored enum — indexing, patent status,
     * award level, event role, project type, publication type. Accreditation
     * bodies ask for exactly these splits, and the schema already carries them,
     * so nothing here is invented or inferred.
     */
    private String breakdownKeyFor(NaacSection section, Achievement a) {
        Enum<?> value = switch (section) {
            case RESEARCH_GRANTS -> a.getResearchGrant() != null
                    ? a.getResearchGrant().getProjectType() : null;
            case PUBLICATIONS_JOURNAL -> a.getPublication() != null
                    ? a.getPublication().getIndexing() : null;
            case PUBLICATIONS_OTHER -> a.getPublication() != null
                    ? a.getPublication().getPublicationType() : null;
            // Never reached: this section carries no breakdown label, so
            // buildSection does not ask it for a key. Present because the switch
            // is exhaustive over the enum, and there is nothing to split on.
            case PUBLICATIONS_UNCLASSIFIED -> null;
            case PATENTS -> a.getPatent() != null ? a.getPatent().getPatentStatus() : null;
            case WORKSHOPS_FDP -> a.getWorkshopFdp() != null ? a.getWorkshopFdp().getRole() : null;
            case AWARDS -> a.getAward() != null ? a.getAward().getAwardLevel() : null;
        };
        return value != null ? value.name() : NOT_SPECIFIED;
    }

    // ------------------------------------------------------------------
    // Row rendering
    //
    // Values are formatted here, once, on the server. The report page and the
    // CSV then render the same strings, so the printed PDF and the downloaded
    // file cannot disagree about a date or an amount.
    // ------------------------------------------------------------------

    private List<String> cellsFor(NaacSection section, Achievement a) {
        User u = a.getUser();
        String dept = txt(u != null && u.getDepartment() != null ? u.getDepartment().getName() : null);
        String name = txt(u != null ? u.getFullName() : null);
        String empId = txt(u != null ? u.getEmployeeId() : null);

        return switch (section) {
            case RESEARCH_GRANTS -> {
                ResearchGrant g = a.getResearchGrant();
                yield List.of(dept, name, empId,
                        txt(g != null && g.getProjectTitle() != null ? g.getProjectTitle() : a.getTitle()),
                        txt(g != null ? g.getFundingAgency() : null),
                        plain(g != null ? g.getGrantAmount() : null),
                        name(g != null ? g.getProjectType() : null),
                        num(g != null ? g.getDurationMonths() : null),
                        name(g != null ? g.getGrantStatus() : null),
                        txt(a.getAcademicYear()), date(a.getAchievementDate()));
            }
            case PUBLICATIONS_JOURNAL -> {
                Publication p = a.getPublication();
                yield List.of(dept, name, empId, txt(a.getTitle()),
                        txt(p != null ? p.getJournalConferenceName() : null),
                        txt(p != null ? p.getPublisher() : null),
                        txt(p != null ? p.getIsbnIssn() : null),
                        txt(p != null ? p.getVolume() : null),
                        txt(p != null ? p.getIssue() : null),
                        txt(p != null ? p.getPages() : null),
                        plain(p != null ? p.getImpactFactor() : null),
                        name(p != null ? p.getIndexing() : null),
                        txt(p != null ? p.getDoi() : null),
                        txt(a.getAcademicYear()), date(a.getAchievementDate()));
            }
            case PUBLICATIONS_OTHER -> {
                Publication p = a.getPublication();
                yield List.of(dept, name, empId, txt(a.getTitle()),
                        name(p != null ? p.getPublicationType() : null),
                        txt(p != null ? p.getJournalConferenceName() : null),
                        txt(p != null ? p.getPublisher() : null),
                        txt(p != null ? p.getIsbnIssn() : null),
                        txt(p != null ? p.getPages() : null),
                        name(p != null ? p.getIndexing() : null),
                        txt(p != null ? p.getDoi() : null),
                        txt(a.getAcademicYear()), date(a.getAchievementDate()));
            }
            // No detail row exists for these, so every publication-specific
            // column would be blank. Only what the portal actually knows is
            // printed, rather than padding the table with empty headings.
            case PUBLICATIONS_UNCLASSIFIED -> List.of(dept, name, empId, txt(a.getTitle()),
                    txt(a.getAcademicYear()), date(a.getAchievementDate()));
            case PATENTS -> {
                Patent pt = a.getPatent();
                yield List.of(dept, name, empId, txt(a.getTitle()),
                        txt(pt != null ? pt.getPatentNumber() : null),
                        name(pt != null ? pt.getPatentStatus() : null),
                        txt(pt != null ? pt.getCountry() : null),
                        date(pt != null ? pt.getFilingDate() : null),
                        date(pt != null ? pt.getGrantDate() : null),
                        txt(a.getAcademicYear()));
            }
            case WORKSHOPS_FDP -> {
                WorkshopFdp w = a.getWorkshopFdp();
                yield List.of(dept, name, empId,
                        txt(w != null && w.getEventName() != null ? w.getEventName() : a.getTitle()),
                        name(w != null ? w.getEventType() : null),
                        name(w != null ? w.getRole() : null),
                        txt(w != null ? w.getOrganizingBody() : null),
                        txt(w != null ? w.getLocation() : null),
                        num(w != null ? w.getDurationDays() : null),
                        txt(a.getAcademicYear()), date(a.getAchievementDate()));
            }
            case AWARDS -> {
                Award aw = a.getAward();
                yield List.of(dept, name, empId,
                        txt(aw != null && aw.getAwardName() != null ? aw.getAwardName() : a.getTitle()),
                        txt(aw != null ? aw.getAwardingBody() : null),
                        name(aw != null ? aw.getAwardLevel() : null),
                        txt(a.getAcademicYear()), date(a.getAchievementDate()));
            }
        };
    }

    private String txt(String v) {
        return v == null ? "" : v;
    }

    private String name(Enum<?> v) {
        return v == null ? "" : v.name();
    }

    private String num(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String date(LocalDate v) {
        return v == null ? "" : v.toString();
    }

    /**
     * Decimals are written unformatted — {@code 1500000.00}, not
     * {@code 15,00,000.00}.
     *
     * <p>Thousands separators would read better on the printed page, but the same
     * string goes into the CSV, where a grouped figure becomes a quoted text cell
     * that a spreadsheet cannot add up. A number an assessor can sum matters more
     * than a comma. Used for grant amounts and impact factors alike.
     */
    private String plain(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    // ------------------------------------------------------------------
    // CSV export
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public byte[] exportCsv(String fromYear, String toYear, Long departmentId, String actorEmail) {
        NaacReportResponse report = generate(fromYear, toYear, departmentId, actorEmail);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(Csv.bom());
        try (PrintWriter pw = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            writeHeader(pw, report);
            writeMatrix(pw, "Summary — approved records by department and academic year",
                    report.getAcademicYears(), report.getSummary());
            for (NaacSectionResponse section : report.getSections()) {
                writeSection(pw, report, section);
            }
        }

        auditLogService.logAction(AuditAction.REPORT_EXPORTED, "REPORT", null,
                auditDescription(report), actorEmail, null);

        return baos.toByteArray();
    }

    private void writeHeader(PrintWriter pw, NaacReportResponse r) {
        pw.println(Csv.cell(r.getReportTitle()));
        pw.println(pair("Generated at", r.getGeneratedAt()));
        pw.println(pair("Generated by", r.getGeneratedByName()));
        pw.println(pair("Departments", r.getDepartmentFilterName() != null
                ? r.getDepartmentFilterName() : "All departments"));
        pw.println(pair("Academic years", yearScope(r)));
        pw.println();

        // The coverage block goes near the top, not in a footnote. A reader who
        // stops after the first screen should still know what is missing.
        NaacReportCoverage c = r.getCoverage();
        pw.println(Csv.cell("Coverage — only verified (approved) records are counted"));
        pw.println(pair("Approved records included", String.valueOf(c.getApprovedIncluded())));
        pw.println(pair("Pending, excluded (awaiting departmental verification)",
                String.valueOf(c.getPendingExcluded())));
        pw.println(pair("Rejected, excluded", String.valueOf(c.getRejectedExcluded())));
        if (c.getUnclassifiedExcluded() > 0) {
            pw.println(pair("Excluded — category has no report section",
                    String.valueOf(c.getUnclassifiedExcluded())));
        }
        if (c.isRowCapReached()) {
            pw.println(pair("INCOMPLETE", "More than the " + c.getRowCap()
                    + "-row limit matched. Narrow the year range or filter by department "
                    + "to export the rest."));
        }
        pw.println();
        pw.println(pair("Note", r.getMetricRefNote()));
        pw.println();
    }

    private void writeMatrix(PrintWriter pw, String heading,
                             List<String> years, List<NaacCountRow> rows) {
        pw.println(Csv.cell(heading));
        List<String> header = new ArrayList<>();
        header.add("Department");
        header.addAll(years);
        header.add("Total");
        // Guarded as user data: the year columns are derived from stored values,
        // so they are not ours to assume are safe.
        pw.println(joinCells(header, true));
        for (NaacCountRow row : rows) {
            List<String> line = new ArrayList<>();
            line.add(row.getDepartmentName());
            for (String year : years) {
                line.add(String.valueOf(row.getCountsByYear().getOrDefault(year, 0L)));
            }
            line.add(String.valueOf(row.getTotal()));
            pw.println(joinCells(line, true));
        }
        pw.println();
    }

    private void writeSection(PrintWriter pw, NaacReportResponse r, NaacSectionResponse s) {
        pw.println(Csv.cell(s.getTitle()));
        pw.println(Csv.cell(s.getSubtitle()));
        if (s.getMetricRef() != null && !s.getMetricRef().isBlank()) {
            pw.println(pair("Metric reference", s.getMetricRef()));
        }
        pw.println(pair("Records", String.valueOf(s.getTotal())));
        if (s.getTotalAmount() != null) {
            pw.println(pair("Total sanctioned amount (INR)", s.getTotalAmount().toPlainString()));
        }
        pw.println();

        if (s.getTotal() == 0) {
            pw.println(Csv.cell("No approved records in this section for the selected scope."));
            pw.println();
            return;
        }

        writeMatrix(pw, s.getTitle() + " — by department and academic year",
                r.getAcademicYears(), s.getCountsByDepartmentYear());

        // A section with no meaningful split carries an empty breakdown; emitting
        // the block anyway would print a heading ending in a dangling "— by ".
        if (!s.getBreakdown().isEmpty()) {
            pw.println(Csv.cell(s.getTitle() + " — by " + s.getBreakdownLabel().toLowerCase(Locale.ROOT)));
            pw.println(joinCells(List.of(s.getBreakdownLabel(), "Records"), false));
            for (Map.Entry<String, Long> e : s.getBreakdown().entrySet()) {
                pw.println(joinCells(List.of(e.getKey(), String.valueOf(e.getValue())), true));
            }
            pw.println();
        }

        pw.println(joinCells(s.getColumns(), false));
        for (List<String> row : s.getRows()) {
            pw.println(joinCells(row, true));
        }
        pw.println();
    }

    /**
     * @param userData true for values that came from user input, which get the
     *                 extra formula-injection guard in {@link Csv#textCell}.
     *                 Our own column headings and labels do not need it.
     */
    private String joinCells(List<String> values, boolean userData) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(userData ? Csv.textCell(values.get(i)) : Csv.cell(values.get(i)));
        }
        return sb.toString();
    }

    private String pair(String label, String value) {
        return Csv.cell(label) + "," + Csv.textCell(value);
    }

    private String yearScope(NaacReportResponse r) {
        if (r.getFromYear() == null && r.getToYear() == null) return "All years";
        if (r.getFromYear() != null && r.getToYear() != null) {
            return r.getFromYear() + " to " + r.getToYear();
        }
        return r.getFromYear() != null ? "From " + r.getFromYear() : "Up to " + r.getToYear();
    }

    /**
     * The audit entry names the slice that left the building — filters and row
     * count, no record contents, no secrets.
     */
    private String auditDescription(NaacReportResponse r) {
        return "Exported accreditation report as CSV (scope: "
                + (r.getDepartmentFilterName() != null ? r.getDepartmentFilterName() : "all departments")
                + ", years: " + yearScope(r)
                + ", rows: " + r.getCoverage().getApprovedIncluded() + ")";
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private User loadActor(String actorEmail) {
        return userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
