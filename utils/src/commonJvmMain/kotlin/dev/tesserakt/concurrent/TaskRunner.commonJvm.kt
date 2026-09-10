package dev.tesserakt.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

class ThreadedTaskRunner private constructor(
    private val executor: ExecutorService,
    private val threadCount: Int,
): TaskRunner {

    companion object {

        operator fun invoke(executor: ExecutorService): TaskRunner {
            val cpuCount = Runtime.getRuntime().availableProcessors()
            val threadCount = if (executor is ThreadPoolExecutor) {
                executor.maximumPoolSize
            } else {
                // we assume an unbounded executor service, spawning threads as is
                //  necessary to process tasks
                Int.MAX_VALUE
            }.coerceAtMost(cpuCount)
            // can't really be negative, but if we get a weird pool that has no threads
            //  available, or we end up on a machine that somehow only has 1 physical core
            //  (e.g. a VM) we don't want to use any of our threading logic
            return if (threadCount <= 0 || cpuCount == 1) {
                TaskRunner.SingleThreaded
            } else {
                ThreadedTaskRunner(executor, threadCount)
            }
        }

    }

    @JvmInline
    private value class FutureResult<T>(val inner: Future<Result<T>>): TaskRunner.TaskResult<T> {
        override fun await(): Result<T> {
            return inner.get()
        }
    }

    @JvmInline
    private value class FutureCollectionResult(val inner: Collection<TaskRunner.TaskResult<Unit>>): TaskRunner.TaskResult<Unit> {
        override fun await(): Result<Unit> {
            val results = inner.map { it.await() }
            val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            return if (failure != null) {
                Result.failure(failure)
            } else {
                Result.success(Unit)
            }
        }
    }

    override fun <T> dispatch(task: () -> T): TaskRunner.TaskResult<T> {
        val callable = Callable {
            runCatching {
                task()
            }
        }
        return FutureResult(executor.submit(callable))
    }

    override fun parallelize(maxCount: Int, block: () -> Unit): TaskRunner.TaskResult<Unit> {
        if (maxCount <= 0) {
            throw IllegalArgumentException("At least one worker is required, got ${maxCount}!")
        }
        // we can't count ourselves as a background worker
        val backgroundWorkers = (threadCount - 1).coerceAtMost(maxCount - 1)
        if (backgroundWorkers <= 0) {
            // we're the only one running
            return TaskRunner.SingleThreaded.dispatch(block)
        }
        val dispatchedResults = List(backgroundWorkers) { dispatch(block) }
        return FutureCollectionResult(
            // we both dispatch our background workers, as well as the calling thread,
            //  so we get some extra parallelization going without scheduling overhead
            inner = dispatchedResults + TaskRunner.SingleThreaded.dispatch(block)
        )
    }

    override fun <T : Any> buffered(source: Iterator<T>): TaskRunner.BufferedIterator<T> {
        val iterator = SpinLockBufferedIterator(source)
        executor.submit {
            iterator.producerLoop()
            // we reached here, so we can safely close the input source if necessary
            if (source is AutoCloseable) {
                source.close()
            }
        }
        return iterator
    }

    internal class SpinLockBufferedIterator<T : Any>(
        private val source: Iterator<T>,
    ): TaskRunner.BufferedIterator<T> {

        companion object {

            // read is the lowest 16 bits; so we inverse it to get the write mask
            private const val READ_MASK = 0xFFFF

            private const val WRITE_MASK = 0xFFFF.inv()

        }

        /**
         * Whether the producer is still active. This is guaranteed to be false if an error was raised.
         */
        @Volatile
        private var alive = true

        // in case a failure occurred, we re-throw it for every reader
        @Volatile
        private var error: Throwable? = null

        // we buffer up to 16 elements - we claim these per reader using our state below
        private val buffer = Array<Any?>(16) { null }
        // state tracking which slots are claimed by the writer / a reader thread:
        // the 16 MSBs indicate which slot have been written to, whilst the 16 LSBs indicate which are claimed for
        //  reading
        // this is stored in a single atomic variable, so write and read state changes are all processed atomically
        private val state = AtomicInteger(0)

        override fun supportsConcurrentAccess(): Boolean {
            // our use of atomics allows for multiple readers to concurrently advance the buffer state
            return true
        }

        /**
         * The method that is run as long as there are new values that can be obtained, and is still being processed
         *  by the receiver.
         */
        fun producerLoop() {
            while (alive) {
                // we get the next element, or the fact that we're EOF
                if (source.hasNext()) {
                    runCatching {
                        source.next()
                    }.fold(
                        onSuccess = { value ->
                            // we wait until we get a slot we can fill up
                            val i = claimWriteSlot()
                            if (i == -1) {
                                return
                            }
                            buffer[i] = value
                            // we mark this slot now as occupied
                            // the write bits are the 16 MSBs, so we apply the offset
                            this.state.setMask(1 shl (i + 16))
                        },
                        onFailure = { exception ->
                            // we want the consumer to 'receive' the exception, so we keep the exception,
                            //  so all consumers become aware, and terminate
                            this.error = exception
                            // we fully clear the state so the error is immediately reported and no reader ends up stuck
                            this.state.set(0)
                            // because we no longer mark ourselves 'alive', the fact that we didn't mark it as
                            //  available is not a problem - consumers are also exiting their spin loop
                            alive = false
                            return
                        }
                    )
                } else {
                    alive = false
                    return
                }
            }
        }

        override fun getNext(): T? {
            val i = claimReadSlot()
            if (i == -1) {
                val error = error
                if (error != null) {
                    throw error
                }
                return null
            }
            // we claimed an index; we get its result and mark it available for writing again
            // followed by marking its slot available for both writing and reading again as well, as we got our result
            val next = buffer[i]
            this.state.unsetMask(((1 shl i) or (1 shl (i + 16))).inv())
            // and we can return the result
            @Suppress("UNCHECKED_CAST")
            return next as T
        }

        override fun close() {
            alive = false
            // we don't close the iterator here, this is the responsibility of the producer thread, as we don't have
            //  concurrency guarantees of source iterator
        }

        /**
         * Claims an index [0, 31], or returns `-1` if no slot could ever be obtained ([alive] is false)
         */
        private fun claimWriteSlot(): Int {
            var state = this.state.get()
            // as long as all write slots are occupied (1), we can't write anywhere
            while (alive && (state and WRITE_MASK) == WRITE_MASK) {
                spinLoopHint()
                state = this.state.get()
            }
            if (!alive) {
                // unfortunate case: an element was likely processed, but is no longer
                //  required downstream as we have been closed
                return -1
            }
            // we find an index we can occupy, which is the # of the first bit that is 0
            return ((state and WRITE_MASK).inv() and WRITE_MASK).takeLowestOneBit().countTrailingZeroBits() - 16
        }

        /**
         * Claims an index [0, 31], or returns `-1` if no slot could ever be
         *  obtained ([error] is set or [alive] is false)
         */
        private fun claimReadSlot(): Int {
            // we want to continue reading / claiming as long as the producer is alive or has left an item in the
            //  buffer for us to consume
            while (true) {
                var state = this.state.get()
                // those written to, but not yet being read from, are available for us
                // using the read mask as we otherwise keep the copied sign bit
                var available = ((state shr 16) and (state.inv())) and READ_MASK
                while ((alive || state and WRITE_MASK != 0) && available == 0) {
                    spinLoopHint()
                    state = this.state.get()
                    // using the read mask as we otherwise keep the copied sign bit
                    available = ((state shr 16) and (state.inv())) and READ_MASK
                }
                // no longer alive, either due to an error, or due to reaching the end of the input
                if (available == 0) {
                    return -1
                }
                // we claim an available index to read: written to but not yet being read from
                val i = available.takeLowestOneBit().countTrailingZeroBits()
                // we try to claim it *once*
                // if it fails, it means another thread got to update the 'read' status first, meaning our 'available'
                //  state is out of date
                if (this.state.compareAndSet(state, state or (1 shl i))) {
                    // we claimed it successfully
                    return i
                }
                // we're fighting another thread, so we go back up top
                spinLoopHint()
            }
        }

    }

}

/**
 * Uses CAS to ensure all **high** bits in [mask] are also set in this integer's value
 */
private fun AtomicInteger.setMask(mask: Int) {
    var value = this.get()
    while (!this.compareAndSet(value, value or mask)) {
        spinLoopHint()
        value = this.get()
    }
}

/**
 * Uses CAS to ensure all **low** bits in [mask] are also **unset** in this integer's value
 */
private fun AtomicInteger.unsetMask(mask: Int) {
    var value = this.get()
    while (!this.compareAndSet(value, value and mask)) {
        spinLoopHint()
        value = this.get()
    }
}
