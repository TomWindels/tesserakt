package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import java.io.BufferedReader


@InternalSerializationApi
class BufferedDataStream(private val reader: BufferedReader): DataStream {

    override fun read(target: CharArray, offset: Int, count: Int): Int {
        return reader.read(target, offset, count)
    }

    override fun close() {
        reader.close()
    }

}
