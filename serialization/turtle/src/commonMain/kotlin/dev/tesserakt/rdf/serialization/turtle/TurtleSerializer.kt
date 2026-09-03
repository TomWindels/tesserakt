package dev.tesserakt.rdf.serialization.turtle

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.DeserializationException
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.common.SuspendingDataSource
import dev.tesserakt.rdf.serialization.util.BufferedCharStream
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

    override suspend fun deserialize(input: SuspendingDataSource): SuspendingDeserializationProcess = try {
        // the deserialization process is responsible for keeping the stream data coming in, so we need
        //  that stream instance here; we can then wrap that stream instance directly into the buffered string
        //  wrapper to process the incoming data
        @OptIn(InternalSerializationApi::class)
        val stream = input.open()
        @OptIn(InternalSerializationApi::class)
        val source = BufferedString(stream)
        @OptIn(InternalSerializationApi::class)
        val deserializer = TurtleDeserializer(
            base = config.base,
            source = TurtleTokenDecoder(source)
        )
        @OptIn(InternalSerializationApi::class)
        SuspendingDeserializationProcess(
            source = stream,
            inner = deserializer,
        )
    } catch (t: Throwable) {
        @OptIn(InternalSerializationApi::class)
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
                source = TurtleTokenDecoder(source),
            )
            return DeserializationProcess(
                source = source,
                inner = deserializer
            )
        }

        override suspend fun deserialize(input: SuspendingDataSource): SuspendingDeserializationProcess = try {
            // the deserialization process is responsible for keeping the stream data coming in, so we need
            //  that stream instance here; we can then wrap that stream instance directly into the buffered string
            //  wrapper to process the incoming data
            @OptIn(InternalSerializationApi::class)
            val stream = input.open()
            @OptIn(InternalSerializationApi::class)
            val source = BufferedString(stream)
            @OptIn(InternalSerializationApi::class)
            val deserializer = TurtleDeserializer(
                source = TurtleTokenDecoder(source)
            )
            @OptIn(InternalSerializationApi::class)
            SuspendingDeserializationProcess(
                source = stream,
                inner = deserializer,
            )
        } catch (t: Throwable) {
            @OptIn(InternalSerializationApi::class)
            throw DeserializationException("Failed to initiate deserialization", t)
        }

    }

}
