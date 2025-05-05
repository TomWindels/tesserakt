package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import java.io.BufferedReader
import java.nio.CharBuffer


@InternalSerializationApi
actual typealias DataSourceStream = BufferedReader

@InternalSerializationApi
actual fun dataSourceStreamOf(text: String): DataSourceStream {
    return text.byteInputStream().bufferedReader()
}

@InternalSerializationApi
actual fun DataSourceStream.read(count: Int): String? {
    val buf = CharBuffer.allocate(count)
    if (read(buf) == -1) {
        return null
    }
    // flipping instead of rewinding, as otherwise the whole `count` capacity is returned in `toString()`, which returns
    //  some funny characters
    return buf.flip().toString()
}
