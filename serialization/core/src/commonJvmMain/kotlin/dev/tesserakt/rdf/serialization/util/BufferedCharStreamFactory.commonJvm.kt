package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.FileDataSource
import java.io.FileInputStream
import java.io.InputStreamReader

@OptIn(markerClass = [InternalSerializationApi::class])
actual fun BufferedCharStream(input: DataSource): BufferedCharStream = when (input) {
    is FileDataSource -> FileCharStream(
        source = InputStreamReader(
            /* in = */ FileInputStream(input.file),
            /* charsetName = */ "UTF-8"
        )
    )
    else -> BufferedString(input.open())
}