package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.serialization.common.TextDataSource
import java.io.FileInputStream
import java.io.InputStreamReader

@OptIn(markerClass = [InternalSerializationApi::class, DelicateSerializationApi::class])
actual fun BufferedCharStream(input: DataSource): BufferedCharStream = when (input) {
    is FileDataSource -> FileCharStream(
        source = InputStreamReader(
            /* in = */ FileInputStream(input.file),
            /* charsetName = */ input.encoding,
        )
    )
    is TextDataSource -> FileCharStream(
        source = input.text.reader(),
    )
    else -> BufferedString(input.open())
}