package dev.tesserakt.rdf.serialization.core

expect class DataSourceStream: AutoCloseable {
    override fun close()
}

internal expect fun dataSourceStreamOf(text: String): DataSourceStream

expect fun DataSourceStream.read(count: Int): String?
