package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import java.io.BufferedReader
import java.nio.CharBuffer


@InternalSerializationApi
class BufferedDataSourceStream(private val reader: BufferedReader): DataSourceStream {

    override fun read(count: Int): String? {
        val buf = CharBuffer.allocate(count)
        if (reader.read(buf) == -1) {
            return null
        }
        // flipping instead of rewinding, as otherwise the whole `count` capacity is returned in `toString()`, which returns
        //  some funny characters
        return buf.flip().toString()
    }

    override fun close() {
        reader.close()
    }

}
