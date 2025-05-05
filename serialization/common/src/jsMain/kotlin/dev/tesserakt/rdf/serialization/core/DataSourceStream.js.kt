package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi


@InternalSerializationApi
actual class DataSourceStream(private val content: String) : AutoCloseable {

    private var pos = 0

    actual override fun close() {
        // nothing to do
    }

    internal fun read(count: Int): String? {
        if (pos >= content.length) {
            return null
        }
        val result = content.substring(pos, (pos + count).coerceAtMost(content.length))
        pos += count
        return result
    }

}

@InternalSerializationApi
actual fun dataSourceStreamOf(text: String): DataSourceStream {
    return DataSourceStream(content = text)
}

@InternalSerializationApi
actual fun DataSourceStream.read(count: Int): String? {
    return read(count)
}
