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
): BufferedCharStream {

    private val buffer = CircularCharBuffer(capacity)
    // immediately populating the buffer for the first time, and storing when we've reached the end of the source
    private var finished = !buffer.read(source)

    // used in `consumeAndDecodeWithoutWhitespaceUntil`
    private val scratch = StringBuilder(200)

    /**
     * Reads the current top character, returning `null` if EOF has been reached.
     */
    override fun peek(): Char? {
        // we have to make sure there's enough data in the buffer
        ensureBufferSize(1)
        // if the buffer is exhausted, and we're looking for a character past it's size, we can conclusively say we've
        //  reached EOF
        if (buffer.size == 0) {
            return null
        }
        return buffer.first()
    }

    private fun ensureBufferSize(size: Int) {
        while (!finished && buffer.size < size) {
            finished = !buffer.read(source)
        }
    }

    override fun consumeAndDecodeWithoutWhitespaceUntil(
        delimiter: Char,
    ): String {
        scratch.setLength(0)
        ensureBufferSize(1)
        if (buffer.size == 0) {
            bail("Unexpected EOF reached! No data remaining!")
        }
        while (true) {
            var i = 0
            val iter = buffer.iterator()
            check(iter.hasNext()) { "$iter failed!"}
            while (iter.hasNext()) {
                val c = iter.nextChar()
                if (c.isWhitespace()) {
                    // we haven't consumed the bad character yet, but we also have to highlight it, so range ends at `1`
                    bail("Invalid character encountered: `$c`", -scratch.length .. 1)
                } else if (c == delimiter) {
                    buffer.consumeInto(scratch, i)
                    return scratch.toString()
                } else if (c == '\\') {
                    // we flush here, and ensure we have more characters loaded into memory
                    buffer.consumeInto(scratch, i)
                    // we also need to look ahead a bit more, if possible
                    ensureBufferSize(10)
                    // making sure our index is also up to date
                    i = 0
                    when (iter.nextChar()) {
                        'u' -> {
                            val codePoint = EscapeSequenceHelper.hexToInt(
                                one = iter.nextChar(),
                                two = iter.nextChar(),
                                three = iter.nextChar(),
                                four = iter.nextChar(),
                            )
                            scratch.appendCodePoint(codePoint)
                            // consumed
                            buffer.consume(6)
                        }
                        'U' -> {
                            val codePoint = EscapeSequenceHelper.hexToInt(
                                one = iter.nextChar(),
                                two = iter.nextChar(),
                                three = iter.nextChar(),
                                four = iter.nextChar(),
                                five = iter.nextChar(),
                                six = iter.nextChar(),
                                seven = iter.nextChar(),
                                eight = iter.nextChar(),
                            )
                            scratch.appendCodePoint(codePoint)
                            // consumed
                            buffer.consume(10)
                        }
                        else -> {
                            bail("Invalid escape sequence encountered!", 0..1)
                        }
                    }
                    // as we mutated the buffer, we have to get out of this loop iteration
                    break
                }
                ++i
            }
            // flushing everything we currently have and resetting our position
            buffer.consumeInto(scratch, i)
            ensureBufferSize(1)
            if (buffer.size == 0) {
                bail("Unexpected EOF reached! Got `$scratch`")
            }
            // the iterator gets as we're going back up top
        }
    }

    /**
     * Reads the current top + [offset] character (cannot be negative!), returning `null` if EOF has been
     *  reached. Automatically reads as much data in from the [source] depending on the offset.
     *
     * Throws an exception if [offset] exceeds the internal [CircularCharBuffer.capacity].
     */
    override fun peek(offset: Int): Char? {
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
    override fun consume(count: Int) {
        buffer.consume(count)
    }

    /**
     * A helper for a common [peek] and [consume] usage pattern:
     * ```kt
     * val c = peek(0)
     * if (c != null) consume(1)
     * ```
     */
    override fun pop(): Char? {
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

    override fun report(index: Int): String {
        return buffer.highlight(index)?.prependIndent("| ") ?: "No report available"
    }

    override fun report(start: Int, end: Int): String {
        return buffer.highlight(start, end)?.prependIndent("| ") ?: "No report available"
    }

}
