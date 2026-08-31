package com.niet.facultyachievement.entity;

/**
 * Which part of a highlight image survives the crop.
 *
 * <p>The home page banner is a wide frame and the posters uploaded into it are
 * not: one is portrait, one landscape, one nearly square. They are drawn with
 * CSS {@code object-fit: cover}, which fills the frame completely and throws
 * away whatever does not fit. This enum decides <em>which</em> part is thrown
 * away — a portrait poster whose headline sits at the top should be anchored to
 * {@link #TOP_CENTER}, so the crop eats the empty bottom instead of the words.
 *
 * <p><strong>Why an enum and not a CSS string.</strong> The obvious shortcut
 * would be to let the administrator type an {@code object-position} value
 * ("center 20%") and emit it into a {@code style} attribute. That would put
 * uploaded text directly into a stylesheet — a CSS injection surface reachable
 * by anyone holding MANAGE_HIGHLIGHTS. With a closed set of nine names, the
 * front end maps the name to a pre-written class and no uploaded character ever
 * becomes part of a style value.
 *
 * <p>The names match the {@code focal_point} ENUM in
 * {@code V5__homepage_highlights.sql} exactly, and are persisted as strings
 * ({@code @Enumerated(EnumType.STRING)}) so their order here can change without
 * rewriting stored data.
 */
public enum HighlightFocalPoint {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    /**
     * The CSS class suffix the front end appends to {@code is-focus-}, e.g.
     * {@code TOP_CENTER} becomes {@code is-focus-top-center}.
     *
     * <p>Derived here rather than in JavaScript so the mapping lives beside the
     * values it maps, and so the browser receives a value it can only use as a
     * class name.
     */
    public String cssModifier() {
        return name().toLowerCase().replace('_', '-');
    }
}
