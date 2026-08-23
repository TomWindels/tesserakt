package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi

@InternalSerializationApi
interface BufferedCharStream: AutoCloseable {

    /**
     * Reads the current top character, returning `null` if EOF has been reached.
     */
    fun peek(): Char?

    /**
     * Consumes data from this backing buffer, decoding `\uXXXX` and `\UXXXXXXXX` segments, returning the read string
     *  instance. Throws an exception (halting the deserialization process) if encountering
     *  a [Char.isWhitespace] character
     */
    fun consumeAndDecodeWithoutWhitespaceUntil(
        delimiter: Char,
    ): String

    /**
     * Simpler version of [consumeAndDecodeWithoutWhitespaceUntil], consuming and returning the data until the first
     * [Char.isWhitespace] is encountered.
     */
    fun consumeUntilWhitespace(): String

    /**
     * Generic version of [consumeUntilWhitespace]
     */
    fun consumeWhile(predicate: (Char) -> Boolean): String

    /**
     * Reads the current top + [offset] character (cannot be negative!), returning `null` if EOF has been
     *  reached. Automatically reads as much data in from the source depending on the offset.
     *
     * Throws an exception if [offset] exceeds the internal [CircularCharBuffer.capacity].
     */
    fun peek(offset: Int): Char?

    /**
     * Consumes [count] characters from the underlying data stream, shifting the data returned by [peek].
     *
     * Throws an exception if more characters are being consumed than are remaining in the buffer. For an optional
     *  consume based on the presence of a next character, see [pop].
     */
    fun consume(count: Int = 1)

    /**
     * A helper for a common [peek] and [consume] usage pattern:
     * ```kt
     * val c = peek(0)
     * if (c != null) consume(1)
     * ```
     */
    fun pop(): Char?

    fun report(index: Int = 0): String

    fun report(start: Int, end: Int): String

}
