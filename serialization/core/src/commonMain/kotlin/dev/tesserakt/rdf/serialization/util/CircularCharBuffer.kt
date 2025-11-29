package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

/**
 * A circular buffer, storing characters efficiently for streaming use of various [dev.tesserakt.rdf.serialization.core.DataStream] implementations during
 *  the deserialization process.
 */
@InternalSerializationApi
class CircularCharBuffer(capacity: Int = 1024) {

    private val mask = run {
        val bit = 1 shl (31 - capacity.countLeadingZeroBits())
        bit - 1
    }

    /**
     * The read offset, used in operations such as [get]. When reading moves along and characters are 'consumed', the
     *  value shifts (and can eventually rotate back).
     */
    private var offset = 0

    /**
     * The size of the buffer. Will never exceed the provided [capacity]. Decreases upon [consume]ing characters, and
     *  can increase upon [read]ing data from [dev.tesserakt.rdf.serialization.core.DataStream]s
     */
    var size = 0
        private set

    /**
     * The backing buffer. The capacity is limited based to the [mask] value, as all accesses require masking.
     */
    private val buffer = CharArray(mask + 1)

    /**
     * The total size of the backing buffer. The [size] can never exceed this value.
     */
    val capacity: Int
        get() = buffer.size

    operator fun get(index: Int): Char {
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
        append("capacity: ")
        append(buffer.size)
        append(", size: ")
        append(size)
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

}
