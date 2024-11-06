package dev.tesserakt.rdf.serialization.util

import java.io.FileReader
import java.nio.CharBuffer

actual typealias BufferedReader = java.io.BufferedReader

actual fun BufferedReader.read(count: Int): String? {
    val buf = CharBuffer.allocate(count)
    if (read(buf) == -1) {
        return null
    }
    // flipping instead of rewinding, as otherwise the whole `count` capacity is returned in `toString()`, which returns
    //  some funny characters
    return buf.flip().toString()
}

actual fun String.openAsBufferedReader(): Result<BufferedReader> = runCatching {
    BufferedReader(FileReader(this))
}

actual fun String.wrapAsBufferedReader(): BufferedReader {
    return this.byteInputStream().bufferedReader()
}
