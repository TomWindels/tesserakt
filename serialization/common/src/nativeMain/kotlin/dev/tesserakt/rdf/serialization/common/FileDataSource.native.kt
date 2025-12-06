package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

actual class FileDataSource actual constructor(val filepath: String): DataSource {
    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataStream {
        TODO("Not yet implemented")
    }
}
