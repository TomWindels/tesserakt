package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi

@InternalSerializationApi
interface SuspendingDataStream: DataStream {

    /**
     * Suspending call periodically invoked, responsible for buffering data obtained through asynchronous means before
     *  being required in [DataStream.read] calls.
     */
    suspend fun prepare()

}
