package io.github.cbrwvy.contextforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CodePointsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "ASCII",
            "café",
            "Привет",
            "مرحبا",
            "こんにちは",
            "hi👍"
    })
    void roundTripsText(String text) {
        long count = CodePoints.count(text);

        assertEquals(text, CodePoints.slice(text, 0, count));
        assertEquals(text.length(), CodePoints.utf16Index(text, count));
    }

    @Test
    void supplementaryCharacterIsOneCodePoint() {
        String thumbsUp = "\uD83D\uDC4D";

        assertEquals(2, thumbsUp.length());
        assertEquals(1, CodePoints.count(thumbsUp));
        assertEquals(0, CodePoints.utf16Index(thumbsUp, 0));
        assertEquals(2, CodePoints.utf16Index(thumbsUp, 1));
        assertEquals(thumbsUp, CodePoints.slice(thumbsUp, 0, 1));
    }

    @Test
    void spanAddressesSupplementaryCharacterInsideText() {
        String text = "a\uD83D\uDC4Db";
        SourceSpan span = SourceSpan.of("notes", 1, 2);

        assertEquals(3, CodePoints.count(text));
        assertEquals(4, text.length());
        assertEquals("\uD83D\uDC4D", CodePoints.slice(text, span.startCodePoint(), span.endCodePoint()));
    }

    @Test
    void spanMayAddressTheInteriorOfAZwjSequence() {
        String family = new String(new int[] {0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467}, 0, 5);

        assertEquals(5, CodePoints.count(family));
        assertEquals("\u200D", CodePoints.slice(family, 1, 2));
        assertEquals(family, CodePoints.slice(family, 0, 5));
    }

    @Test
    void combiningMarkIsItsOwnCodePoint() {
        String decomposed = "e\u0301";

        assertEquals(2, CodePoints.count(decomposed));
        assertEquals("e", CodePoints.slice(decomposed, 0, 1));
        assertEquals("\u0301", CodePoints.slice(decomposed, 1, 2));
    }

    @Test
    void unpairedSurrogateCountsAsOneUnit() {
        String surrogate = "\uD800";

        assertEquals(1, CodePoints.count(surrogate));
        assertEquals(surrogate, CodePoints.slice(surrogate, 0, 1));
    }

    @Test
    void emptySliceIsEmpty() {
        assertEquals("", CodePoints.slice("abc", 2, 2));
    }

    @Test
    void rejectsNullText() {
        assertEquals("text", assertThrows(NullPointerException.class, () -> CodePoints.count(null)).getMessage());
        assertThrows(NullPointerException.class, () -> CodePoints.utf16Index(null, 0));
        assertThrows(NullPointerException.class, () -> CodePoints.slice(null, 0, 0));
    }

    @Test
    void rejectsOffsetOutsideText() {
        assertThrows(IllegalArgumentException.class, () -> CodePoints.utf16Index("ab", -1));
        assertThrows(IllegalArgumentException.class, () -> CodePoints.utf16Index("ab", 3));
        assertThrows(IllegalArgumentException.class, () -> CodePoints.slice("ab", 0, 3));
        assertThrows(IllegalArgumentException.class, () -> CodePoints.slice("ab", 2, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CodePoints.utf16Index("ab", (long) Integer.MAX_VALUE + 1));
    }
}
