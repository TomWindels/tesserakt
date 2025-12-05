package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

class StringIterableSource(private val source: Iterable<String>): DataSource {

    @OptIn(InternalSerializationApi::class)
    private class Stream(private val iterator: Iterator<String>): DataStream {

        private var curr = if (iterator.hasNext()) iterator.next() else null
        private var remaining = curr?.length ?: -1

        override fun read(target: CharArray, offset: Int, count: Int): Int {
            val curr = curr ?: return -1
            return if (remaining > count) {
                val start = curr.length - remaining
                curr.toCharArray(
                    destination = target,
                    destinationOffset = offset,
                    startIndex = start,
                    endIndex = start + count,
                )
                remaining -= count
                count
            } else {
                val inserted = remaining
                curr.toCharArray(
                    destination = target,
                    destinationOffset = offset,
                    startIndex = curr.length - remaining,
                    endIndex = curr.length,
                )
                if (iterator.hasNext()) {
                    val next = iterator.next()
                    this.curr = next
                    remaining = next.length
                } else {
                    this.curr = null
                    remaining = -1
                }
                inserted
            }
        }

        override fun close() {
            // nothing to do
        }

    }

    @OptIn(InternalSerializationApi::class)
    override fun open(): DataStream {
        return Stream(iterator = source.iterator())
    }

}
