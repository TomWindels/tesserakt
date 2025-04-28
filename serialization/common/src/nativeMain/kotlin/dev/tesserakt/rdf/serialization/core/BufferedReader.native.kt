package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.Source

actual class BufferedReader(private val content: String) : AutoCloseable {

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

actual fun BufferedReader.read(count: Int): String? {
    return read(count)
}

@OptIn(DelicateSerializationApi::class)
actual fun Source.open() = when (this) {
    is Source.Text -> {
        BufferedReader(text)
    }

    is Source.File -> {
        TODO("Not yet implemented")
    }
}
