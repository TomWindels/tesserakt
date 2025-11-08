package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream

class StringIterableSource(private val source: Iterable<String>): DataSource {

    @OptIn(InternalSerializationApi::class)
    private class Stream(private val iterator: Iterator<String>): DataSourceStream {

        private var i = 0
        private var curr = if (iterator.hasNext()) iterator.next() else null

        override fun read(count: Int): String? {
            val curr = curr ?: return null
            if (count + i < curr.length) {
                val result = curr.substring(i, i + count)
                i += count
                return result
            }
            val result = StringBuilder(count)
            result.append(curr, i, curr.length)
            while (true) {
                val next = if (iterator.hasNext()) {
                    iterator.next()
                } else {
                    this.curr = null
                    break
                }
                if (result.length + next.length > count) {
                    i = count - result.length
                    result.append(next, 0, count - result.length)
                    this.curr = next
                    break
                } else if (result.length + next.length == count) {
                    result.append(next)
                    i = 0
                    this.curr = if (iterator.hasNext()) iterator.next() else null
                    break
                } else /* < count */ {
                    result.append(next)
                }
            }
            return result.toString()
        }

        override fun close() {
            // nothing to do
        }

    }

    @OptIn(InternalSerializationApi::class)
    override fun open(): DataSourceStream {
        return Stream(iterator = source.iterator())
    }

}
