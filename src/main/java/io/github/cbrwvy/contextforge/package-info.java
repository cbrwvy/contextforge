/**
 * ContextForge prepares text for later processing and keeps the result traceable
 * to the source.
 *
 * <p>Public offsets count Unicode code points, meaning scalar values, in decoded
 * text. A span is a half-open range and stores each end as a {@code long}, so a
 * stream may contain more than {@link Integer#MAX_VALUE} code points. Convert
 * Java UTF-16 indexes with {@link io.github.cbrwvy.contextforge.CodePoints}.
 */
package io.github.cbrwvy.contextforge;
