package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.SuspendingDataStream

/**
 * A special [DataSource] variant that can `suspend` before opening a [SuspendingDataStream] instance and/or
 *  during [SuspendingDataStream.prepare]. If the implementation does not require `suspend`ing the execution
 *  while preparing data, consider implementing the [DataSource] interface instead.
 */
interface SuspendingDataSource {
    @OptIn(InternalSerializationApi::class)
    suspend fun open(): SuspendingDataStream
}
