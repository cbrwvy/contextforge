package io.github.cbrwvy.contextforge;

import java.util.Objects;

/**
 * Conversions between a Java character sequence and Unicode scalar-value offsets.
 *
 * <p>An unpaired UTF-16 surrogate is not a scalar value. A read that would consume
 * one throws {@link IllegalArgumentException}. The message carries the UTF-16 index
 * and omits the text. A high surrogate followed by a low surrogate is one scalar
 * value. The helper does not normalize text and does not snap a range to a grapheme
 * cluster, so a span may begin or end inside one.
 *
 * <p>Only the units a call must read are validated. Streaming sources apply the same
 * check as they read, and that path is not implemented here.
 */
public final class CodePoints {

    private CodePoints() {
    }

    /**
     * Returns the number of Unicode scalar values in {@code text}.
     *
     * @throws IllegalArgumentException if a read consumes an unpaired surrogate
     */
    public static long count(CharSequence text) {
        Objects.requireNonNull(text, "text");
        int length = text.length();
        long count = 0;
        for (int index = 0; index < length; ) {
            index += scalarUnits(text, index);
            count++;
        }
        return count;
    }

    /**
     * Returns the UTF-16 index of the scalar value at {@code codePointOffset}.
     * An offset equal to the number of scalar values before that position returns
     * the corresponding UTF-16 index, including {@code text.length()} at the end.
     * Units past the requested offset are not read.
     *
     * @throws IllegalArgumentException if {@code codePointOffset} is negative, past the
     *     well-formed prefix, or a read would consume an unpaired surrogate
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
            index += scalarUnits(text, index);
            seen++;
        }
        if (seen == codePointOffset) {
            return limit;
        }
        throw new IllegalArgumentException(
                "codePointOffset is past the end of the text: " + codePointOffset);
    }

    /**
     * Returns the scalar values in {@code [startCodePoint, endCodePoint)} as a string.
     *
     * @throws IllegalArgumentException if the range is inverted, falls outside the
     *     well-formed prefix, or a read would consume an unpaired surrogate
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

    /**
     * Returns the number of UTF-16 units in the scalar value at {@code index}.
     */
    private static int scalarUnits(CharSequence text, int index) {
        char unit = text.charAt(index);
        if (Character.isHighSurrogate(unit)) {
            int next = index + 1;
            if (next < text.length() && Character.isLowSurrogate(text.charAt(next))) {
                return 2;
            }
            throw unpairedSurrogate(index);
        }
        if (Character.isLowSurrogate(unit)) {
            throw unpairedSurrogate(index);
        }
        return 1;
    }

    private static IllegalArgumentException unpairedSurrogate(int utf16Index) {
        return new IllegalArgumentException("unpaired surrogate at UTF-16 index " + utf16Index);
    }
}
