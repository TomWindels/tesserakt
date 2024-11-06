package dev.tesserakt.rdf.serialization.util

expect class BufferedReader: AutoCloseable {
    override fun close()
}

expect fun BufferedReader.read(count: Int): String?

expect fun String.openAsBufferedReader(): Result<BufferedReader>

expect fun String.wrapAsBufferedReader(): BufferedReader
