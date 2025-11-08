package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.BufferedDataSourceStream
import dev.tesserakt.rdf.serialization.core.DataSourceStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


@JvmInline
value class StreamDataSource(private val stream: InputStream) : DataSource {
    @OptIn(InternalSerializationApi::class)
    override fun open(): DataSourceStream {
        return BufferedDataSourceStream(BufferedReader(InputStreamReader(stream, "UTF-8")))
    }
}
