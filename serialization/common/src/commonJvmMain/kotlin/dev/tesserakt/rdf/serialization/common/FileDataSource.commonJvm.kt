package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream
import java.io.FileReader

actual class FileDataSource(private val file: java.io.File) : DataSource {
    actual constructor(filepath: String): this(file = java.io.File(filepath))

    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataSourceStream {
        return java.io.BufferedReader(FileReader(file))
    }
}
