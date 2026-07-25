package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.SuspendingDataStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Converts a [Node.js stream](https://nodejs.org/api/stream.htm) into a [SuspendingDataStream] that can be used as
 *  input for [Serializer.deserialize]ing.
 *
 * Note that the stream can only be consumed once, throwing [NoSuchElementException] upon calling [open] again (e.g.
 *  when [Serializer.deserialize]ing an already deserialized stream).
 */
class NodeStreamDataSource(private var stream: dynamic): SuspendingDataSource {

    @OptIn(InternalSerializationApi::class)
    private class Stream(private val stream: dynamic, first: String): SuspendingDataStream {

        private val inner = StringQueueStream(first)
        private var finished = false

        override suspend fun prepare() {
            while (inner.count < 2 && !finished) {
                val new = awaitNextElement(stream) as String?
                if (new == null) {
                    finished = true
                } else {
                    inner.enqueue(new)
                }
            }
        }

        override fun read(target: CharArray, offset: Int, count: Int): Int {
            val c = inner.read(target, offset, count)
            if (c == -1 && !finished) {
                throw IllegalStateException("Could not read enough of the read stream in time!")
            }
            return c
        }

        override fun close() {
            inner.close()
            closeStream(stream)
        }
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun open(): SuspendingDataStream {
        val stream = this.stream
            ?: throw NoSuchElementException("Stream was already consumed!")
        this.stream = null
        val first = awaitNextElement(stream) as String?
        if (first == null) {
            closeStream(stream)
            return EmptyStream
        }
        return Stream(
            stream = stream,
            first = first,
        )
    }

}

/* helpers */

private suspend fun awaitNextElement(stream: dynamic) = suspendCoroutine { continuation ->
    fun cleanup() {
        stream.removeAllListeners("data")
        stream.removeAllListeners("error")
        stream.removeAllListeners("close")
    }
    stream.once("data") { chunk ->
        cleanup()
        continuation.resume(chunk)
    }
    stream.once("error") { cause ->
        cleanup()
        continuation.resumeWithException(cause)
    }
    stream.once("close") {
        cleanup()
        continuation.resume(null)
    }
}

private fun closeStream(stream: dynamic) {
    stream.push(null)
    stream.read(0)
}
