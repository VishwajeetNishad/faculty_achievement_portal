package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.HomepageHighlight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HomepageHighlightRepository extends JpaRepository<HomepageHighlight, Long> {

    /**
     * The only query the public endpoint ever runs: live slides, in the order
     * the administrator arranged them.
     *
     * <p>{@code idAsc} is the tie-break. Without it, two slides sharing a
     * display order would come back in whatever sequence MySQL felt like, and
     * the carousel would silently reshuffle itself between page loads.
     *
     * <p>The {@code active} filter is in the query rather than in the service so
     * a retired banner cannot be published by a caller forgetting to filter.
     * Matched by {@code idx_highlights_active_order}.
     */
    List<HomepageHighlight> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    /**
     * Every slide, live or retired, for the admin table.
     *
     * <p>Fetch-joins the uploader because the admin list shows who added each
     * banner. Left as a JOIN FETCH rather than a lazy read: the response is
     * built outside a transaction, and a list of twenty highlights would
     * otherwise fire twenty extra queries for twenty names.
     */
    @Query("SELECT h FROM HomepageHighlight h "
            + "JOIN FETCH h.uploadedBy "
            + "ORDER BY h.displayOrder ASC, h.id ASC")
    List<HomepageHighlight> findAllWithUploader();

    /** One slide with its uploader loaded, for the response after a write. */
    @Query("SELECT h FROM HomepageHighlight h JOIN FETCH h.uploadedBy WHERE h.id = :id")
    Optional<HomepageHighlight> findByIdWithUploader(Long id);

    /**
     * The highest display order currently in use, so a new upload lands at the
     * end of the carousel instead of jumping to the front.
     *
     * <p>{@code COALESCE} matters: on an empty table {@code MAX} returns null,
     * and {@code null + 1} in Java would be a NullPointerException at the moment
     * the very first banner is uploaded — the least convenient time to discover
     * it.
     */
    @Query("SELECT COALESCE(MAX(h.displayOrder), 0) FROM HomepageHighlight h")
    int findMaxDisplayOrder();
}
