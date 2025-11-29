package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi


@InternalSerializationApi
class TextDataStream(private val content: String) : DataStream {

    private var pos = 0

    override fun close() {
        // moving the pos out of reach so subsequent `read`s fail
        pos = content.length
    }

    override fun read(target: CharArray, offset: Int, count: Int): Int {
        if (content.length <= pos) {
            return -1
        }
        val len = count.coerceAtMost(content.length - pos)
        content.toCharArray(
            destination = target,
            destinationOffset = offset,
            startIndex = pos,
            endIndex = pos + len,
        )
        pos += len
        return len
    }

}
