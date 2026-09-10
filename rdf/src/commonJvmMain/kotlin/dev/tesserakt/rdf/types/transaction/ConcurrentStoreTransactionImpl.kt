package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.concurrent.SPMCBuffer
import dev.tesserakt.concurrent.TaskRunner
import dev.tesserakt.concurrent.ThreadedTaskRunner
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

internal class ConcurrentStoreTransactionImpl(
    private val parent: MutableStore,
    // we confine ourselves to the runner currently configured
    runner: ThreadedTaskRunner,
    // we assume a fast source calling `add` and `remove`, so we use at most 2 background workers to process quad
    //  changes
    // if there's a reason to
    backgroundWorkers: Int = 2,
) : StoreTransaction() {

    // we enqueue *changes*
    private sealed interface Change {

        @JvmInline
        value class Addition(val element: Quad) : Change

        @JvmInline
        value class Deletion(val element: Quad) : Change

    }

    private val buf: SPMCBuffer<Change>
    private val workers: TaskRunner.TaskResult<Unit>

    init {
        if (backgroundWorkers <= 0) {
            throw IllegalArgumentException("Not enough background workers configured!")
        }
        // we also need to make sure we actually have workers to do the multithreaded work with:
        //  at least 1 thread available for reading our requests, assuming we aren't already occupying that
        //  worker thread
        if (runner.threadCount < 1) {
            throw IllegalStateException("No background workers configured to do concurrent modification with!")
        }
        buf = SPMCBuffer()
        // we can now start processing changes enqueued to this buffer *in the background*
        workers = runner.parallelizeInBackground(backgroundWorkers) {
            while (true) {
                when (val change = buf.poll()) {
                    is Change.Addition -> {
                        parent.add(change.element)
                    }
                    is Change.Deletion -> {
                        parent.remove(change.element)
                    }
                    // end of input, worker can shut down
                    null -> break
                }
            }
        }
    }

    override fun add(quad: Quad) {
        buf.push(Change.Addition(quad))
    }

    override fun remove(quad: Quad) {
        buf.push(Change.Deletion(quad))
    }

    override fun commit() {
        buf.close()
        // blocking until the workers have finished
        workers.await()
    }

}
