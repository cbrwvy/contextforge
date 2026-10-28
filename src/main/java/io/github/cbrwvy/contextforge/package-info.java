/**
 * ContextForge prepares text for later processing and keeps the result traceable
 * to the source.
 *
 * <p>Public offsets count Unicode scalar values in decoded text. An unpaired
 * UTF-16 surrogate is not a scalar value. {@link io.github.cbrwvy.contextforge.CodePoints}
 * rejects one when a read would consume it. A span stores the resulting offsets
 * and does not read source text. Each end is a {@code long}, so a stream may
 * contain more than {@link Integer#MAX_VALUE} scalar values.
 */
package io.github.cbrwvy.contextforge;
