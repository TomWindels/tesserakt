package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

/**
 * A circular buffer, storing characters efficiently for streaming use of various [dev.tesserakt.rdf.serialization.core.DataStream] implementations during
 *  the deserialization process.
 */
@InternalSerializationApi
class CircularCharBuffer(capacity: Int = 1024): DeserializationContextProvider {

    private val mask = run {
        val bit = 1 shl (31 - capacity.countLeadingZeroBits())
        bit - 1
    }

    /**
     * The read offset, used in operations such as [get]. When reading moves along and characters are 'consumed', the
     *  value shifts (and can eventually rotate back).
     *
     * Is only meant to be used in [getRaw].
     */
    override var offset = 0
        private set

    /**
     * The size of the buffer. Will never exceed the provided [capacity]. Decreases upon [consume]ing characters, and
     *  can increase upon [read]ing data from [dev.tesserakt.rdf.serialization.core.DataStream]s
     */
    override var size = 0
        private set

    /**
     * The backing buffer. The capacity is limited based to the [mask] value, as all accesses require masking.
     */
    private val buffer = CharArray(mask + 1)

    /**
     * A cached iterator that goes through the still-available contents of the backing [buffer].
     */
    private val iterator = object: CharIterator() {

        private var pos = offset
        private var remaining = size

        override fun nextChar(): Char {
            if (remaining == 0) {
                throw NoSuchElementException()
            }
            val c = buffer[pos++]
            --remaining
            if (pos == buffer.size) {
                pos = 0
            }
            return c
        }

        override fun hasNext(): Boolean {
            return remaining != 0
        }

        fun reset() {
            pos = offset
            remaining = size
        }

    }

    /**
     * The total size of the backing buffer. The [size] can never exceed this value.
     */
    override val capacity: Int
        get() = buffer.size

    /**
     * Returns the first character. Identical behaviour as invoking `get(0)`. Throws [NoSuchElementException] if the
     *  buffer is empty ([size] is `0`)
     */
    fun first(): Char {
        if (size == 0) {
            throw NoSuchElementException("The buffer is empty!")
        }
        // no need to mask the offset, it's guaranteed to be in the [0..capacity[ range
        return buffer[offset]
    }

    /**
     * Gets the character at the given [index].
     *
     * Throws [IllegalArgumentException] if [index]` < 0`.
     * Throws [NoSuchElementException] if [index] exceeds the buffer's [size].
     */
    operator fun get(index: Int): Char {
        if (index < 0) {
            throw IllegalArgumentException("Index cannot be negative, was $index")
        }
        if (index >= size) {
            throw NoSuchElementException("Index $index exceeds size $size")
        }
        val mapped = (offset + index) and mask
        return buffer[mapped]
    }

    fun consume(count: Int = 1) {
        require(count > 0) { "Trying to consume an invalid amount of data: $count" }
        require(count <= size) { "Trying to consume more data than is currently read in: $count > $size" }
        offset = (offset + count) and mask
        size -= count
    }

    fun consumeInto(target: StringBuilder, count: Int) {
        require(size >= count)
        if (offset + count > buffer.size) {
            // we need to do two inserts
            target.appendRange(buffer, offset, buffer.size)
            target.appendRange(buffer, 0, count - (buffer.size - offset))
        } else {
            // simple insert suffices
            target.appendRange(buffer, offset, offset + count)
        }
        // we now completely consume the contents by updating our state
        offset = (offset + count) and mask
        size -= count
    }

    /**
     * Returns a specialised iterator for faster element access.
     *
     * Uses a single cached instance, meaning that you cannot have multiple active at the same time.
     *
     * Buffer modification is not allowed whilst the iterator instance is in use.
     */
    fun iterator(): CharIterator {
        iterator.reset()
        return iterator
    }

    /**
     * Reads as many characters as possible from [source]. Returns `true` if new characters have been added or the
     *  source hasn't been exhausted yet.
     */
    fun read(source: DataStream): Boolean {
        if (size == buffer.size) {
            // we're at capacity, no reads can be done, and so we cannot detect whether the source has been exhausted
            return true
        }
        val end = (size + offset) and mask
        if (end >= offset) {
            // case [ .<#2>. offset .<in-use>. end .<#1>. ]
            // #1
            val tail = buffer.size - end
            if (tail > 0) {
                val read = source.read(target = buffer, offset = end, count = tail)
                if (read < 0) {
                    // EOF
                    return false
                }
                size += read
                if (read < tail) {
                    // no point in trying a second read, but there are no guarantees we've reached the end yet
                    return true
                }
            }
            // #2
            // we can also read from the start of the buffer until the offset position
            val head = offset
            if (head > 0) {
                val read = source.read(target = buffer, offset = 0, count = head)
                if (read < 0) {
                    // whilst we reached EOF, we have added data at the tail position if that is strictly positive
                    // if we haven't added anything to the tail, then we've reached EOF without reading anything
                    return tail > 0
                }
                size += read
            }
            return true
        } else {
            // case [ .<in-use>. end .<#1>. offset .<in-use>. ]
            // #1
            val between = offset - end
            if (between <= 0) {
                // we cannot read anything; so we don't know if the source has any data pending
                return true
            }
            val read = source.read(target = buffer, offset = end, count = between)
            if (read < 0) {
                // EOF
                return false
            }
            size += read
            return true
        }
    }

    override fun toString(): String = buildString {
        append("CircularCharBuffer { ")
        append("offset: ")
        append(offset)
        append(", size: ")
        append(size)
        append(", capacity: ")
        append(buffer.size)
        append(", contents: [")
        val end = (size + offset) and mask
        buffer.forEachIndexed { i, c ->
            append(' ')
            if (i == end) {
                append('*')
            }
            if (i == offset) {
                append('>')
            }
            append("0x")
            append(c.code.toHexString())
            if (i == offset) {
                append('<')
            }
            if (i == end) {
                append('*')
            }
            append(' ')
        }
        append("] }")
    }

    override fun getRaw(index: Int): Char {
        return buffer[index and mask]
    }

    override fun findContextWindow(indexStart: Int, indexEnd: Int, context: DeserializationContextProvider.ContextWindow): Pair<Int, Int> {
        val end = (size + offset) and mask

        var startOffset = 0
        var endOffset = 0

        val maxLengthBefore = if (indexStart < end) {
            indexStart + (buffer.size - end)
        } else {
            end - indexStart
        }
        val maxLengthAfter = if (indexEnd < end) {
            end - indexEnd
        } else {
            (buffer.size - indexEnd) + end
        }

        while (
            maxLengthBefore > startOffset &&
            // making sure there's actual data here
            buffer[(indexStart - startOffset) and mask].code != 0 &&
            context.includeBefore(startOffset, buffer[(indexStart - startOffset) and mask])
        ) {
            ++startOffset
        }

        while (
            maxLengthAfter > endOffset &&
            context.includeAfter(endOffset, buffer[(indexEnd + endOffset) and mask])
        ) {
            ++endOffset
        }

        // the two offsets now point just outside the region, so we adjust both before returning
        return ((indexStart - startOffset + 1) and mask) to ((indexEnd + endOffset - 1) and mask)
    }

}
