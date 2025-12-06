package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream

/**
 * Represents an arbitrary data source, which is used as an input during [Serializer.deserialize]ing.
 *
 * Creates [DataStream]s used during the deserialization process.
 */
interface DataSource {
    @OptIn(InternalSerializationApi::class)
    fun open(): DataStream
}
