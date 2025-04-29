package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.core.DataSourceStream
import dev.tesserakt.rdf.serialization.core.dataSourceStreamOf
import kotlin.jvm.JvmInline

@DelicateSerializationApi
@JvmInline
value class TextDataSource(val text: String) : DataSource {
    override fun open(): DataSourceStream = dataSourceStreamOf(text)
}
