package com.niet.facultyachievement.dto.report;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One printed section of the report — e.g. "Research Publications — Journals".
 *
 * <p><strong>Why {@code rows} is {@code List<List<String>>} and not a typed DTO.</strong>
 * The six sections have six different column sets. A generic row shape lets one
 * frontend renderer and one CSV writer serve all six, instead of twelve
 * near-identical code paths. It also buys a correctness property worth more than
 * the type safety it gives up: every value is formatted <em>once</em>, on the
 * server, so the printed PDF and the downloaded CSV cannot disagree about a date
 * or an amount — they render the same strings.
 *
 * <p>{@code columns} and each row are the same length and the same order. That
 * contract is the whole interface; if you add a column to
 * {@link NaacSection}, add the matching cell in the service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NaacSectionResponse {

    /** {@link NaacSection#name()}, for stable DOM ids and CSV section headers. */
    private String sectionKey;

    private String title;
    private String subtitle;

    /** Blank unless the institution has filled it in — see {@link NaacSection}. */
    private String metricRef;

    private List<String> columns;

    /** This section's department x year counts. Same shape as the report summary. */
    @Builder.Default
    private List<NaacCountRow> countsByDepartmentYear = new ArrayList<>();

    /**
     * The section's enum split — indexing for journals, status for patents, and
     * so on. Keys are the real enum values present in the data; nothing is
     * invented and nothing absent is padded in.
     */
    @Builder.Default
    private Map<String, Long> breakdown = new LinkedHashMap<>();

    /** What {@code breakdown} counts, e.g. "Indexing". Printed as its heading. */
    private String breakdownLabel;

    /**
     * Sum of sanctioned grant amounts, for the grants section only; null
     * elsewhere. {@code BigDecimal} because money must not accumulate the drift
     * a {@code double} would.
     */
    private BigDecimal totalAmount;

    /** Detail rows, aligned to {@link #columns}. */
    @Builder.Default
    private List<List<String>> rows = new ArrayList<>();

    private long total;
}
