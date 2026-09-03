package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource

@OptIn(markerClass = [InternalSerializationApi::class])
actual fun BufferedCharStream(input: DataSource): BufferedCharStream {
    return BufferedString(input.open())
}
