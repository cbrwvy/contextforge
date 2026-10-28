package io.github.cbrwvy.contextforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ticket-export",
            " a",
            "a b",
            "مصدر",
            "文档",
            "e\u0301",
            "\u00e9",
            "\u00a0"
    })
    void acceptsNonBlankValues(String value) {
        Identifier identifier = new Identifier(value);

        assertEquals(value, identifier.value());
    }

    @Test
    void keepsDistinctValuesDistinct() {
        assertEquals(new Identifier("ticket-export"), new Identifier("ticket-export"));
        assertNotEquals(new Identifier("e\u0301"), new Identifier("\u00e9"));
        assertNotEquals(new Identifier("a b"), new Identifier("ab"));
        assertNotEquals(new Identifier(" a"), new Identifier("a"));
    }

    @Test
    void rejectsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new Identifier(null));

        assertEquals("value", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n", " \n\t "})
    void rejectsBlankValues(String value) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Identifier(value));

        assertEquals("identifier must not be blank", exception.getMessage());
    }
}
