package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi


@InternalSerializationApi
fun dataSourceStreamOf(text: String): DataSourceStream {
    return TextDataSourceStream(content = text)
}
