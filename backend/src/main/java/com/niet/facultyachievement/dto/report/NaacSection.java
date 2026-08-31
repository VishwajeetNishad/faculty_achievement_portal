package com.niet.facultyachievement.dto.report;

import java.util.List;

/**
 * The sections of the accreditation research-output report, in the order they
 * are printed.
 *
 * <p><strong>About {@code metricRef} — read this before filling it in.</strong>
 * NAAC publishes three separate assessment manuals — University, Autonomous
 * College, and Affiliated/Constituent College — and assigns different weightages
 * across the seven criteria. The metric numbering is therefore <em>not</em> the
 * same for every institution, so this class ships every {@code metricRef} empty
 * rather than guessing. Whoever handles accreditation at the institution should
 * fill these in from the current manual that applies to <em>this</em> college,
 * and nowhere else: this enum is the single place the report reads them from,
 * and a blank one simply prints nothing.
 *
 * <p>Do not populate these from memory or from another college's report. A wrong
 * metric number on a submitted document is worse than an absent one, because an
 * absent one is obviously incomplete while a wrong one looks authoritative.
 *
 * <p>The {@code title} of each section, by contrast, is always correct: it
 * describes what the rows actually are, in terms of this portal's own data.
 */
public enum NaacSection {

    RESEARCH_GRANTS(
            "Research Grants & Funded Projects",
            "Sanctioned, ongoing and completed funded research and consultancy projects",
            "",
            "Project type",
            List.of("Department", "Faculty", "Employee ID", "Project Title", "Funding Agency",
                    "Amount (INR)", "Project Type", "Duration (Months)", "Grant Status",
                    "Academic Year", "Date")),

    PUBLICATIONS_JOURNAL(
            "Research Publications — Journals",
            "Papers published in journals, with indexing and impact factor",
            "",
            "Indexing",
            List.of("Department", "Faculty", "Employee ID", "Title", "Journal", "Publisher",
                    "ISSN", "Volume", "Issue", "Pages", "Impact Factor", "Indexing", "DOI",
                    "Academic Year", "Date")),

    PUBLICATIONS_OTHER(
            "Research Publications — Books, Chapters & Conference Proceedings",
            "Books, book chapters and papers in conference proceedings",
            "",
            "Publication type",
            List.of("Department", "Faculty", "Employee ID", "Title", "Publication Type",
                    "Book / Conference", "Publisher", "ISBN / ISSN", "Pages", "Indexing", "DOI",
                    "Academic Year", "Date")),

    /**
     * Approved publications whose detail row was never created, so the portal
     * does not know what kind of publication they are.
     *
     * <p>These cannot go in either typed section above without the report
     * asserting a type nobody recorded — printing them under "Books, Chapters &
     * Conference Proceedings" would claim a book that may well be a journal
     * paper. They are not dropped either: they are approved research output, and
     * an accreditation report that quietly omits records is the thing this
     * whole design avoids. So they are listed under a heading that is true, with
     * only the columns that actually hold data.
     *
     * <p>In a clean database this section is empty, which is exactly what it
     * should look like. A non-zero count is a prompt to complete those records
     * before the report is submitted, not a research claim.
     */
    PUBLICATIONS_UNCLASSIFIED(
            "Research Publications — Type Not Recorded",
            "Approved publications with no publication details saved; "
                    + "complete these records before submitting the report",
            "",
            "",
            List.of("Department", "Faculty", "Employee ID", "Title", "Academic Year", "Date")),

    PATENTS(
            "Patents & Intellectual Property",
            "Patents filed, published and granted",
            "",
            "Patent status",
            List.of("Department", "Faculty", "Employee ID", "Title", "Patent Number", "Status",
                    "Country", "Filing Date", "Grant Date", "Academic Year")),

    WORKSHOPS_FDP(
            "Workshops, FDPs & Certifications",
            "Programmes organised by the institution and attended by its faculty",
            "",
            "Role",
            List.of("Department", "Faculty", "Employee ID", "Event", "Event Type", "Role",
                    "Organising Body", "Location", "Duration (Days)", "Academic Year", "Date")),

    AWARDS(
            "Awards & Recognition",
            "Honours and awards received from recognised bodies",
            "",
            "Award level",
            List.of("Department", "Faculty", "Employee ID", "Award", "Awarding Body", "Level",
                    "Academic Year", "Date"));

    private final String title;
    private final String subtitle;
    private final String metricRef;
    private final String breakdownLabel;
    private final List<String> columns;

    NaacSection(String title, String subtitle, String metricRef,
                String breakdownLabel, List<String> columns) {
        this.title = title;
        this.subtitle = subtitle;
        this.metricRef = metricRef;
        this.breakdownLabel = breakdownLabel;
        this.columns = columns;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    /** Empty by design — see the class comment before setting one. */
    public String getMetricRef() {
        return metricRef;
    }

    /** What the per-section breakdown counts, e.g. "Indexing" for journals. Empty when a section has no meaningful split. */
    public String getBreakdownLabel() {
        return breakdownLabel;
    }

    public List<String> getColumns() {
        return columns;
    }
}
