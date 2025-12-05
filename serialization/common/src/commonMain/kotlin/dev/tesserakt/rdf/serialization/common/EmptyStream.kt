package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.SuspendingDataStream

// a [SuspendingDataStream] is also a [DataStream], so implementing that interface
@OptIn(InternalSerializationApi::class)
internal object EmptyStream: SuspendingDataStream {

    override suspend fun prepare() {
        // nothing to do
    }

    override fun read(target: CharArray, offset: Int, count: Int): Int {
        return -1
    }

    override fun close() {
        // nothing to do
    }

}
