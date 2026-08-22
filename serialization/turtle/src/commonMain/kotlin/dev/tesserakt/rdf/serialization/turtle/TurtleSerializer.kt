package dev.tesserakt.rdf.serialization.turtle

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.DeserializationException
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.util.BufferedCharStream
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
    override fun deserialize(input: DataSource): DeserializationProcess = try {
        val source = BufferedCharStream(input)
        val deserializer = TurtleDeserializer(
            base = config.base,
            source = TurtleTokenDecoder(source),
        )
        return DeserializationProcess(
            source = source,
            inner = deserializer
        )
    } catch (t: Throwable) {
        throw DeserializationException("Failed to initiate deserialization", t)
    }

    companion object: Serializer() {
        override fun serialize(store: Store): Iterator<String> {
            return SimpleTurtleFormatter.format(TurtleTokenEncoder(store.iterator()))
        }

        override fun serialize(data: Iterator<Quad>): Iterator<String> {
            return SimpleTurtleFormatter.format(TurtleTokenEncoder(data))
        }

        @OptIn(InternalSerializationApi::class)
        override fun deserialize(input: DataSource): DeserializationProcess {
            val source = BufferedCharStream(input)
            val deserializer = TurtleDeserializer(
                base = "",
                source = TurtleTokenDecoder(source),
            )
            return DeserializationProcess(
                source = source,
                inner = deserializer
            )
        }
    }

}
