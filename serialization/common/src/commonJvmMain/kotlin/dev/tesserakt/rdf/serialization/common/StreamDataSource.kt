package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.core.DataSourceStream
import java.io.InputStream
import java.io.InputStreamReader


@JvmInline
value class StreamDataSource(private val stream: InputStream) : DataSource {
    override fun open(): DataSourceStream {
        return java.io.BufferedReader(InputStreamReader(stream, "UTF-8"))
    }
}
