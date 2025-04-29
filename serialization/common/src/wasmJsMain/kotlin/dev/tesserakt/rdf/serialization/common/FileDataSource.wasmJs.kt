package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.core.DataSourceStream

actual class FileDataSource actual constructor(val filepath: String) : DataSource {
    actual override fun open(): DataSourceStream {
        throw NotImplementedError("The WASM implementation currently does not support file IO")
    }
}
