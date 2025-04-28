package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.Source
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader
import java.nio.CharBuffer


actual typealias BufferedReader = BufferedReader

actual fun BufferedReader.read(count: Int): String? {
    val buf = CharBuffer.allocate(count)
    if (read(buf) == -1) {
        return null
    }
    // flipping instead of rewinding, as otherwise the whole `count` capacity is returned in `toString()`, which returns
    //  some funny characters
    return buf.flip().toString()
}

@OptIn(DelicateSerializationApi::class)
actual fun Source.open(): BufferedReader = when (this) {
    is Source.File -> {
        BufferedReader(FileReader(file))
    }
    is Source.Stream -> {
        BufferedReader(InputStreamReader(stream, "UTF-8"))
    }
    is Source.Text -> {
        text.byteInputStream().bufferedReader()
    }
}
