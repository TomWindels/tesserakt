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

    fun estimatedSize(): Int {
        // no actual estimate by default;
        // 10 is the default for most collections in the JVM, so we reuse that value
        return 10
    }

}
