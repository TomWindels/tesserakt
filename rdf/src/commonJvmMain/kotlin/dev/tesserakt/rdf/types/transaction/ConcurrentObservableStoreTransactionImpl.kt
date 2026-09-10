package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.concurrent.MPSCBuffer
import dev.tesserakt.concurrent.SPMCBuffer
import dev.tesserakt.concurrent.TaskRunner
import dev.tesserakt.concurrent.ThreadedTaskRunner
import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.impl.ObservableStoreImpl

internal class ConcurrentObservableStoreTransactionImpl(
    // we can't mark it both observable & concurrent; we assume it is concurrent
    private val parent: ObservableStoreImpl,
    // we confine ourselves to the runner currently configured
    runner: ThreadedTaskRunner,
    // we assume a fast source calling `add` and `remove`, so we use at most 2 background workers to process quad
    //  changes
    // this number does not include the extra worker used to
    backgroundWorkers: Int = 2,
) : StoreTransaction() {

    // we enqueue *changes*
    private sealed interface DataChange {

        @JvmInline
        value class Addition(val element: Quad) : DataChange

        @JvmInline
        value class Deletion(val element: Quad) : DataChange

    }

    private sealed interface CollectionChange {

        data class Addition(val element: Quad, val encoded: EncodedQuad) : CollectionChange

        data class Deletion(val element: Quad, val encoded: EncodedQuad) : CollectionChange

    }

    private val buf1: SPMCBuffer<DataChange>
    private val workers: TaskRunner.TaskResult<Unit>
    // the `onQuad{Added,Removed}` callbacks cannot be called concurrently, so we use another buffer to reduce it back
    //  to another thread
    private val buf2: MPSCBuffer<CollectionChange>
    private val callbackHandler: TaskRunner.TaskResult<Unit>

    init {
        if (backgroundWorkers == 0) {
            throw IllegalArgumentException("Not enough background workers configured!")
        }
        // we also need to make sure we actually have workers to do the multithreaded work with:
        //  * at least 1 thread available for reading our requests, assuming we aren't already occupying that
        //  worker thread
        //  * at least 1 thread available for processing changes downstream, assuming we aren't already occupying that
        //  worker thread
        if (runner.threadCount < 2) {
            throw IllegalStateException("Not enough background workers available to do concurrent modification with!")
        }
        buf1 = SPMCBuffer()
        buf2 = MPSCBuffer()
        // we can now start processing changes enqueued to this buffer *in the background*
        callbackHandler = runner.dispatch {
            while (true) {
                when (val change = buf2.poll()) {
                    is CollectionChange.Addition -> {
                        parent.onQuadAdded(change.element, change.encoded)
                    }
                    is CollectionChange.Deletion -> {
                        parent.onQuadRemoved(change.element, change.encoded)
                    }
                    // end of input, worker can shut down
                    null -> break
                }
            }
        }
        workers = runner.parallelizeInBackground(backgroundWorkers) {
            while (true) {
                when (val change = buf1.poll()) {
                    is DataChange.Addition -> {
                        val encoded = EncodedQuad(parent.context, change.element)
                        if (parent.inner.add(encoded)) {
                            buf2.push(CollectionChange.Addition(change.element, encoded))
                        }
                    }
                    is DataChange.Deletion -> {
                        val encoded = EncodedQuad(parent.context, change.element)
                        if (parent.inner.remove(encoded)) {
                            buf2.push(CollectionChange.Deletion(change.element, encoded))
                        }
                    }
                    // end of input, worker can shut down
                    null -> break
                }
            }
        }
    }

    override fun add(quad: Quad) {
        buf1.push(DataChange.Addition(quad))
    }

    override fun remove(quad: Quad) {
        buf1.push(DataChange.Deletion(quad))
    }

    override fun commit() {
        buf1.close()
        buf2.close()
        // blocking until the workers have finished
        workers.await()
        callbackHandler.await()
    }

}
