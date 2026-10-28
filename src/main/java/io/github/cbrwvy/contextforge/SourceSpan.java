package io.github.cbrwvy.contextforge;

import java.util.Objects;

/**
 * A half-open range of Unicode code points {@code [startCodePoint, endCodePoint)}
 * in one identified source.
 *
 * <p>Offsets count Unicode scalar values in well-formed decoded text, before
 * optional normalization. A span does not read that text. {@link CodePoints}
 * rejects an unpaired UTF-16 surrogate when a read would consume it. An empty
 * span, where the two ends are equal, is valid. Either end may be greater than
 * {@link Integer#MAX_VALUE}.
 */
public record SourceSpan(Identifier sourceId, long startCodePoint, long endCodePoint) {

    public SourceSpan {
        Objects.requireNonNull(sourceId, "sourceId");
        if (startCodePoint < 0) {
            throw new IllegalArgumentException("startCodePoint must be >= 0: " + startCodePoint);
        }
        if (endCodePoint < startCodePoint) {
            throw new IllegalArgumentException(
                    "endCodePoint must be >= startCodePoint: " + startCodePoint + ", " + endCodePoint);
        }
    }

    /**
     * Returns a span for a caller-supplied source id.
     *
     * @throws NullPointerException if {@code sourceId} is null
     * @throws IllegalArgumentException if {@code sourceId} is blank, {@code startCodePoint} is negative,
     *     or {@code endCodePoint} is less than {@code startCodePoint}
     */
    public static SourceSpan of(String sourceId, long startCodePoint, long endCodePoint) {
        Objects.requireNonNull(sourceId, "sourceId");
        return new SourceSpan(new Identifier(sourceId), startCodePoint, endCodePoint);
    }

    /**
     * Returns {@code endCodePoint - startCodePoint}.
     */
    public long length() {
        return endCodePoint - startCodePoint;
    }

    public boolean isEmpty() {
        return startCodePoint == endCodePoint;
    }

    /**
     * Returns whether {@code codePointOffset} lies in this half-open range.
     * The end offset is excluded. A negative query offset is outside every span.
     */
    public boolean contains(long codePointOffset) {
        return codePointOffset >= startCodePoint && codePointOffset < endCodePoint;
    }
}
