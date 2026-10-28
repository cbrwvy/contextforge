package io.github.cbrwvy.contextforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceSpanTest {

    @Test
    void representsHalfOpenCodePointRange() {
        SourceSpan span = SourceSpan.of("ticket-export", 2, 5);

        assertEquals(new Identifier("ticket-export"), span.sourceId());
        assertEquals(2, span.startCodePoint());
        assertEquals(5, span.endCodePoint());
        assertEquals(3, span.length());
        assertFalse(span.isEmpty());
        assertTrue(span.contains(2));
        assertTrue(span.contains(4));
        assertFalse(span.contains(5));
        assertFalse(span.contains(1));
        assertFalse(span.contains(-1));
    }

    @Test
    void allowsEmptySpan() {
        SourceSpan span = new SourceSpan(new Identifier("notes"), 4, 4);

        assertTrue(span.isEmpty());
        assertEquals(0, span.length());
        assertFalse(span.contains(4));
        assertEquals(span, SourceSpan.of("notes", 4, 4));
    }

    @Test
    void allowsOffsetsBeyondIntRange() {
        long start = (long) Integer.MAX_VALUE + 1;
        SourceSpan span = SourceSpan.of("archive", start, start + 5);

        assertEquals(start, span.startCodePoint());
        assertEquals(5, span.length());
        assertTrue(span.contains(start));
        assertFalse(span.contains(start + 5));
    }

    @Test
    void allowsRangeEndingAtMaxLong() {
        SourceSpan span = SourceSpan.of("archive", Long.MAX_VALUE - 1, Long.MAX_VALUE);

        assertEquals(1, span.length());
        assertTrue(span.contains(Long.MAX_VALUE - 1));
        assertFalse(span.contains(Long.MAX_VALUE));
    }

    @Test
    void rejectsNullSourceId() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> SourceSpan.of(null, 0, 1));

        assertEquals("sourceId", exception.getMessage());
        assertThrows(NullPointerException.class, () -> new SourceSpan(null, 0, 1));
    }

    @Test
    void rejectsBlankSourceId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SourceSpan.of(" ", 0, 1));

        assertEquals("identifier must not be blank", exception.getMessage());
    }

    @Test
    void rejectsNegativeStart() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SourceSpan.of("notes", -1, 0));

        assertTrue(exception.getMessage().contains("startCodePoint"));
    }

    @Test
    void rejectsEndBeforeStart() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SourceSpan.of("notes", 3, 2));

        assertTrue(exception.getMessage().contains("endCodePoint"));
    }
}
