package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi


@InternalSerializationApi
class TextDataSourceStream(private val content: String) : DataSourceStream {

    private var pos = 0

    override fun close() {
        // nothing to do
    }

    override fun read(count: Int): String? {
        if (pos >= content.length) {
            return null
        }
        val result = content.substring(pos, (pos + count).coerceAtMost(content.length))
        pos += count
        return result
    }

}
