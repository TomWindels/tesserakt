package dev.tesserakt.rdf.serialization.trig

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.DeserializationException
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.common.SuspendingDataSource
import dev.tesserakt.rdf.serialization.util.BufferedCharStream
import dev.tesserakt.rdf.serialization.util.BufferedString
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

    override suspend fun deserialize(input: SuspendingDataSource): SuspendingDeserializationProcess = try {
        // the deserialization process is responsible for keeping the stream data coming in, so we need
        //  that stream instance here; we can then wrap that stream instance directly into the buffered string
        //  wrapper to process the incoming data
        @OptIn(InternalSerializationApi::class)
        val stream = input.open()
        @OptIn(InternalSerializationApi::class)
        val source = BufferedString(stream)
        @OptIn(InternalSerializationApi::class)
        val deserializer = TriGDeserializer(
            base = config.base,
            source = TriGTokenDecoder(source)
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

        override suspend fun deserialize(input: SuspendingDataSource): SuspendingDeserializationProcess = try {
            // the deserialization process is responsible for keeping the stream data coming in, so we need
            //  that stream instance here; we can then wrap that stream instance directly into the buffered string
            //  wrapper to process the incoming data
            @OptIn(InternalSerializationApi::class)
            val stream = input.open()
            @OptIn(InternalSerializationApi::class)
            val source = BufferedString(stream)
            @OptIn(InternalSerializationApi::class)
            val deserializer = TriGDeserializer(
                source = TriGTokenDecoder(source)
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
