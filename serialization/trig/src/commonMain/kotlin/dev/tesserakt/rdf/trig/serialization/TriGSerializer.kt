package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

internal class TriGSerializer(private val config: TriGConfig): Serializer() {

    override fun serialize(store: Store): Iterator<String> {
        return config.formatter.format(TokenEncoder(store.iterator()))
    }

    override fun serialize(data: Iterator<Quad>): Iterator<String> {
        return config.formatter.format(TokenEncoder(data))
    }

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(input: DataStream): Iterator<Quad> {
        return Deserializer(
            base = config.base,
            source = TokenDecoder(BufferedString(input))
        )
    }

    companion object: Serializer() {
        override fun serialize(store: Store): Iterator<String> {
            return DEFAULT_FORMATTER.format(TokenEncoder(store.iterator()))
        }

        override fun serialize(data: Iterator<Quad>): Iterator<String> {
            return DEFAULT_FORMATTER.format(TokenEncoder(data))
        }

        @OptIn(InternalSerializationApi::class)
        override fun deserialize(input: DataStream): Iterator<Quad> {
            return Deserializer(TokenDecoder(BufferedString(input)))
        }
    }

}
