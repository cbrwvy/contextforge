package io.github.cbrwvy.contextforge;

import java.util.Objects;

/**
 * Conversions between a Java character sequence and Unicode code-point offsets.
 *
 * <p>Counts follow {@link Character#codePointAt(CharSequence, int)}. An unpaired
 * surrogate counts as one unit. The helper does not normalize text and does not
 * snap a range to a grapheme cluster, so a span may begin or end inside one.
 */
public final class CodePoints {

    private CodePoints() {
    }

    /**
     * Returns the number of Unicode code points in {@code text}.
     */
    public static long count(CharSequence text) {
        Objects.requireNonNull(text, "text");
        int length = text.length();
        long count = 0;
        for (int index = 0; index < length; ) {
            index += Character.charCount(Character.codePointAt(text, index));
            count++;
        }
        return count;
    }

    /**
     * Returns the UTF-16 index of the code point at {@code codePointOffset}.
     * An offset equal to {@link #count(CharSequence)} returns {@code text.length()}.
     *
     * @throws IllegalArgumentException if {@code codePointOffset} is negative or past the text
     */
    public static int utf16Index(CharSequence text, long codePointOffset) {
        Objects.requireNonNull(text, "text");
        if (codePointOffset < 0) {
            throw new IllegalArgumentException("codePointOffset must be >= 0: " + codePointOffset);
        }
        int limit = text.length();
        int index = 0;
        long seen = 0;
        while (index < limit) {
            if (seen == codePointOffset) {
                return index;
            }
            index += Character.charCount(Character.codePointAt(text, index));
            seen++;
        }
        if (seen == codePointOffset) {
            return limit;
        }
        throw new IllegalArgumentException(
                "codePointOffset is past the end of the text: " + codePointOffset);
    }

    /**
     * Returns the code points in {@code [startCodePoint, endCodePoint)} as a string.
     *
     * @throws IllegalArgumentException if the range is inverted or falls outside {@code text}
     */
    public static String slice(CharSequence text, long startCodePoint, long endCodePoint) {
        Objects.requireNonNull(text, "text");
        if (startCodePoint < 0) {
            throw new IllegalArgumentException("startCodePoint must be >= 0: " + startCodePoint);
        }
        if (endCodePoint < startCodePoint) {
            throw new IllegalArgumentException(
                    "endCodePoint must be >= startCodePoint: " + startCodePoint + ", " + endCodePoint);
        }
        int start = utf16Index(text, startCodePoint);
        int end = utf16Index(text, endCodePoint);
        return text.subSequence(start, end).toString();
    }
}
