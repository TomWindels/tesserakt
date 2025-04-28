package dev.tesserakt.rdf.serialization.util

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

actual fun String.openAsBufferedReader(): Result<BufferedReader> =
    Result.failure(UnsupportedOperationException("The WASM implementation currently has no filesystem integration"))

actual fun String.wrapAsBufferedReader(): BufferedReader {
    return BufferedReader(content = this)
}
