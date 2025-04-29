package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.core.DataSourceStream


expect class FileDataSource(filepath: String) : DataSource {
    override fun open(): DataSourceStream
}
