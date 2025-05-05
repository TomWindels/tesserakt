package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi

@InternalSerializationApi
expect class DataSourceStream: AutoCloseable {
    override fun close()
}

@InternalSerializationApi
internal expect fun dataSourceStreamOf(text: String): DataSourceStream

@InternalSerializationApi
expect fun DataSourceStream.read(count: Int): String?
