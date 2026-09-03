package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.BufferedDataStream
import dev.tesserakt.rdf.serialization.core.DataStream
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

actual class FileDataSource(val file: java.io.File, val encoding: Charset) : DataSource {

    actual constructor(filepath: String, encoding: String):
            this(file = java.io.File(filepath), encoding = Charset.forName(encoding))

    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataStream {
        val stream = InputStreamReader(
            /* in = */ FileInputStream(file),
            /* charsetName = */ "UTF-8"
        )
        return BufferedDataStream(java.io.BufferedReader(stream))
    }

}
