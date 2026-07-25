package dev.tesserakt.rdf.serialization.turtle

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store

internal class TurtleSerializer(private val config: TurtleConfig): Serializer() {

    override fun serialize(store: Store): Iterator<String> {
        return config.formatter.format(TurtleTokenEncoder(store.iterator()))
    }

    override fun serialize(data: Iterator<Quad>): Iterator<String> {
        return config.formatter.format(TurtleTokenEncoder(data))
    }

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(input: DataStream): Iterator<Quad> {
        return TurtleDeserializer(
            base = config.base,
            source = TurtleTokenDecoder(BufferedString(input)),
        )
    }

    companion object: Serializer() {
        override fun serialize(store: Store): Iterator<String> {
            return SimpleTurtleFormatter.format(TurtleTokenEncoder(store.iterator()))
        }

        override fun serialize(data: Iterator<Quad>): Iterator<String> {
            return SimpleTurtleFormatter.format(TurtleTokenEncoder(data))
        }

        @OptIn(InternalSerializationApi::class)
        override fun deserialize(input: DataStream): Iterator<Quad> {
            return TurtleDeserializer(
                base = "",
                source = TurtleTokenDecoder(BufferedString(input)),
            )
        }
    }

}
