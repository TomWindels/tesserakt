package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream


interface DataSource {
    @OptIn(InternalSerializationApi::class)
    fun open(): DataSourceStream
}
