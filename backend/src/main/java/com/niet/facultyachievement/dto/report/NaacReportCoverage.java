package com.niet.facultyachievement.dto.report;

import lombok.*;

/**
 * What the report left out, stated on the report's own face.
 *
 * <p>The report counts only APPROVED achievements, because an accreditation
 * document must not carry unverified claims. But "we counted 412 publications"
 * and "we counted 412 of the 460 publications on record, the rest still awaiting
 * departmental verification" are different statements, and only the second one is
 * honest. This block is what makes the second one printable.
 *
 * <p>{@code rowCapReached} exists for the same reason. The query is capped, like
 * the existing CSV export is — but a silently truncated accreditation report is
 * worse than no report, so when the cap bites, the report says so.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaacReportCoverage {

    /** Rows actually included — every one of them APPROVED. */
    private long approvedIncluded;

    /** On record but not yet verified by a HOD or Admin, so not counted above. */
    private long pendingExcluded;

    /** Reviewed and rejected, so not counted above. */
    private long rejectedExcluded;

    /**
     * Approved records whose category this report has no section for.
     *
     * <p>Normally zero. It stops being zero if a sixth achievement category is
     * ever seeded without a matching section being added to {@code NaacSection} —
     * and the point of the counter is that the report then <em>says</em> so
     * instead of quietly printing a total that is short by however many rows the
     * new category holds. A visible zero costs a line; a silent gap costs the
     * institution's numbers.
     */
    private long unclassifiedExcluded;

    /** True when the row cap truncated the detail tables. */
    private boolean rowCapReached;

    /** The cap in force, so the message can name it instead of being vague. */
    private int rowCap;
}
