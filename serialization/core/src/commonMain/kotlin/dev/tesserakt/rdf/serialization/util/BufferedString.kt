package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

@InternalSerializationApi
class BufferedString(
    private val source: DataStream,
    /**
     * The capacity to use in the internal buffer. The largest power of two will be used as the actual buffer size.
     *  This value affects how far ahead a [peek] can be issued.
     */
    capacity: Int = 1024,
): AutoCloseable {

    private val buffer = CircularCharBuffer(capacity)
    // immediately populating the buffer for the first time, and storing when we've reached the end of the source
    private var finished = !buffer.read(source)

    /**
     * Reads the current top + [offset] character (cannot be negative!), returning `null` if EOF has been
     *  reached. Automatically reads as much data in from the [source] depending on the offset.
     *
     * Throws an exception if [offset] exceeds the internal [CircularCharBuffer.capacity].
     */
    fun peek(offset: Int = 0): Char? {
        if (offset >= buffer.capacity) {
            throw IllegalArgumentException("The offset cannot exceed the buffer's capacity (${offset} >= ${buffer.capacity})")
        }
        // we have to make sure there's enough data in the buffer
        while (!finished && offset >= buffer.size) {
            finished = !buffer.read(source)
        }
        // if the buffer is exhausted, and we're looking for a character past it's size, we can conclusively say we've
        //  reached EOF
        if (offset >= buffer.size) {
            return null
        }
        return buffer[offset]
    }

    /**
     * Consumes [count] characters from the underlying data stream, shifting the data returned by [peek].
     *
     * Throws an exception if more characters are being consumed than are remaining in the buffer. For an optional
     *  consume based on the presence of a next character, see [pop].
     */
    fun consume(count: Int = 1) {
        buffer.consume(count)
    }

    /**
     * A helper for a common [peek] and [consume] usage pattern:
     * ```kt
     * val c = peek(0)
     * if (c != null) consume(1)
     * ```
     */
    fun pop(): Char? {
        val c = peek(0)
        if (c != null) {
            consume()
        }
        return c
    }

    override fun toString(): String {
        return buffer.toString()
    }

    override fun close() {
        source.close()
    }

}
