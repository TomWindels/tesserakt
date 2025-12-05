package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.core.TextDataStream
import kotlin.jvm.JvmInline

@DelicateSerializationApi
@JvmInline
value class TextDataSource(val text: String) : DataSource {
    @OptIn(InternalSerializationApi::class)
    override fun open(): DataStream = TextDataStream(content = text)
}
