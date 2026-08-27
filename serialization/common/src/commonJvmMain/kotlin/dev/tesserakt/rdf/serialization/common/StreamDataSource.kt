package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.BufferedDataStream
import dev.tesserakt.rdf.serialization.core.DataStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset


class StreamDataSource(
    private val stream: InputStream,
    private val encoding: Charset,
) : DataSource {

    constructor(stream: InputStream, encoding: String): this(stream = stream, encoding = Charset.forName(encoding))

    @OptIn(InternalSerializationApi::class)
    override fun open(): DataStream {
        return BufferedDataStream(BufferedReader(InputStreamReader(stream, encoding)))
    }
}
