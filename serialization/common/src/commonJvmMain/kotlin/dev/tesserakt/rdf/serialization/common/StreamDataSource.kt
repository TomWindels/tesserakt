package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.BufferedDataStream
import dev.tesserakt.rdf.serialization.core.DataStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


@JvmInline
value class StreamDataSource(private val stream: InputStream) : DataSource {
    @OptIn(InternalSerializationApi::class)
    override fun open(): DataStream {
        return BufferedDataStream(BufferedReader(InputStreamReader(stream, "UTF-8")))
    }
}
