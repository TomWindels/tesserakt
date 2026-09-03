package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource

@OptIn(InternalSerializationApi::class)
expect fun BufferedCharStream(input: DataSource): BufferedCharStream
