package dev.tesserakt.rdf.serialization.trig

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.util.BufferedCharStream
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

internal class TriGSerializer(private val config: TriGConfig): Serializer() {

    override fun serialize(store: Store): Iterator<String> {
        return config.formatter.format(TriGTokenEncoder(store.iterator()))
    }

    override fun serialize(data: Iterator<Quad>): Iterator<String> {
        return config.formatter.format(TriGTokenEncoder(data))
    }

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(input: DataSource): DeserializationProcess {
        val source = BufferedCharStream(input)
        val deserializer = TriGDeserializer(
            base = config.base,
            source = TriGTokenDecoder(source)
        )
        return DeserializationProcess(
            source = source,
            inner = deserializer,
        )
    }

    companion object: Serializer() {
        override fun serialize(store: Store): Iterator<String> {
            return DEFAULT_TRIG_FORMATTER.format(TriGTokenEncoder(store.iterator()))
        }

        override fun serialize(data: Iterator<Quad>): Iterator<String> {
            return DEFAULT_TRIG_FORMATTER.format(TriGTokenEncoder(data))
        }

        @OptIn(InternalSerializationApi::class)
        override fun deserialize(input: DataSource): DeserializationProcess {
            val source = BufferedCharStream(input)
            val deserializer = TriGDeserializer(
                source = TriGTokenDecoder(source)
            )
            return DeserializationProcess(
                source = source,
                inner = deserializer,
            )
        }
    }

}
