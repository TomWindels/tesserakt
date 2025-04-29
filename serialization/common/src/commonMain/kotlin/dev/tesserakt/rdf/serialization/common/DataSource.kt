package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.core.DataSourceStream


interface DataSource {
    fun open(): DataSourceStream
}
