package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream
import java.io.FileInputStream
import java.io.InputStreamReader

actual class FileDataSource(private val file: java.io.File) : DataSource {
    actual constructor(filepath: String): this(file = java.io.File(filepath))

    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataSourceStream {
        val stream = InputStreamReader(
            /* in = */ FileInputStream(file),
            /* charsetName = */ "UTF-8"
        )
        return java.io.BufferedReader(stream)
    }
}
