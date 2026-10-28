package io.github.cbrwvy.contextforge;

import java.util.Objects;

/**
 * A caller-supplied identifier, such as a source id, a message id, or a pattern id.
 *
 * <p>The value is kept exactly as supplied. Leading or trailing spaces remain part
 * of the identifier. A value is rejected when {@link String#isBlank()} is true.
 * This type does not trim or normalize Unicode.
 */
public record Identifier(String value) {

    public Identifier {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
    }
}
