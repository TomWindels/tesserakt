package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream


expect class FileDataSource(filepath: String) : DataSource {
    @OptIn(InternalSerializationApi::class)
    override fun open(): DataStream
}
