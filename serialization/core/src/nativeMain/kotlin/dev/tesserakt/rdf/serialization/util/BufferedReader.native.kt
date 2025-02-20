package dev.tesserakt.rdf.serialization.util

actual class BufferedReader : AutoCloseable {
    actual override fun close() {
        TODO()
    }
}

actual fun BufferedReader.read(count: Int): String? = TODO()

actual fun String.openAsBufferedReader(): Result<BufferedReader> = TODO()

actual fun String.wrapAsBufferedReader(): BufferedReader = TODO()
