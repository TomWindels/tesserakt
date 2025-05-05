package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream

actual class FileDataSource actual constructor(val filepath: String) : DataSource {
    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataSourceStream {
        throw NotImplementedError("The WASM implementation currently does not support file IO")
    }
}
