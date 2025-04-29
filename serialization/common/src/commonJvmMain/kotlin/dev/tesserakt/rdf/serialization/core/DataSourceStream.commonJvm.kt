package dev.tesserakt.rdf.serialization.core

import java.io.BufferedReader
import java.nio.CharBuffer


actual typealias DataSourceStream = BufferedReader

actual fun dataSourceStreamOf(text: String): DataSourceStream {
    return text.byteInputStream().bufferedReader()
}

actual fun DataSourceStream.read(count: Int): String? {
    val buf = CharBuffer.allocate(count)
    if (read(buf) == -1) {
        return null
    }
    // flipping instead of rewinding, as otherwise the whole `count` capacity is returned in `toString()`, which returns
    //  some funny characters
    return buf.flip().toString()
}
