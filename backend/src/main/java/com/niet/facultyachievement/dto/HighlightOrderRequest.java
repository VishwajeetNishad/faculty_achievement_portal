package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * The new carousel order, as a complete list of highlight ids front to back.
 *
 * <p><strong>Why the whole list and not "move id 7 up one".</strong> A pair of
 * single-step calls is two writes with a gap in the middle: two administrators
 * reordering at once, or one clicking ▲ twice quickly, can interleave and leave
 * the carousel in an order neither of them asked for. Sending the entire sequence
 * makes reordering one atomic statement — the server assigns positions 1..n in
 * the order received, so the result is always exactly what the screen showed.
 *
 * <p>The service additionally rejects the request unless the submitted id set is
 * <em>exactly</em> the set of stored ids. A list missing an id would strand that
 * row at whatever position it held, quietly, which is the kind of bug nobody
 * notices until a visitor sees the wrong poster first.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighlightOrderRequest {

    /** Every highlight id, in the order they should appear. First = first slide. */
    @NotEmpty(message = "The reordered list of highlight ids is required")
    private List<Long> orderedIds;
}
