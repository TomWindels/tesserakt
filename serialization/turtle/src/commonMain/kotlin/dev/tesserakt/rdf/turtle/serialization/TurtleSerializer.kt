package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.common.TextDataSource
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad

class TurtleSerializer(private val config: TurtleConfig): Serializer() {

    override fun serialize(data: Collection<Quad>): Iterator<String> {
        return config.formatter.format(TokenEncoder(data))
    }

    override fun serialize(data: Iterator<Quad>): Iterator<String> {
        return config.formatter.format(TokenEncoder(data))
    }

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(input: DataSource): Iterator<Quad> {
        return Deserializer(TokenDecoder(BufferedString(input.open())))
    }

    companion object: Serializer() {
        override fun serialize(data: Collection<Quad>): Iterator<String> {
            return SimpleFormatter.format(TokenEncoder(data))
        }

        override fun serialize(data: Iterator<Quad>): Iterator<String> {
            return SimpleFormatter.format(TokenEncoder(data))
        }

        @OptIn(InternalSerializationApi::class)
        override fun deserialize(input: DataSource): Iterator<Quad> {
            return Deserializer(TokenDecoder(BufferedString(input.open())))
        }

        @DelicateSerializationApi
        fun String.parseTurtleString() = deserialize(input = TextDataSource(this))
    }

}
