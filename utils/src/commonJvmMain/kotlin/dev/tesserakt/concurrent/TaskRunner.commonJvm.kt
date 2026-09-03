package dev.tesserakt.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class ThreadedTaskRunner(
    private val executor: ExecutorService
): TaskRunner {

    @JvmInline
    value class FutureResult<T>(val inner: Future<Result<T>>): TaskRunner.TaskResult<T> {
        override fun await(): Result<T> {
            return inner.get()
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

    override fun <T : Any> buffered(source: Iterator<T>): TaskRunner.BufferedIterator<T> {
        val iterator = SpinLockBufferedIterator(source)
        // we need to make sure the loop is actually producing data; if there's no threads available, we need to fall
        //  back to a single threaded variant to prevent a deadlock on a resource that never comes
        var started = false
        var beginProducing = false
        val task = executor.submit {
            started = true
            // we need to make sure this iterator is actually getting used; we wait until we get the go-ahead
            var remaining = 100_000
            while (!beginProducing && --remaining > 0) {
                spinLoopHint()
            }
            if (remaining == 0) {
                // we executed successfully, but we were not
                return@submit
            }
            iterator.producerLoop()
            // we reached here, so we can safely close the input source if necessary
            if (source is AutoCloseable) {
                source.close()
            }
        }
        var remaining = 100_000
        while (remaining > 0 && !started) {
            --remaining
        }
        if (remaining > 0) {
            // the thread started successfully, and we intend to use this parallel iterator, so we allow the producer
            //  loop to start
            beginProducing = true
            return iterator
        }
        // the iterator failed to start, so we shut it down and fall back to single threaded eval
        iterator.close()
        // we won't use the buffered iterator anymore, so we can cancel its task
        task.cancel(false)
        return TaskRunner.SingleThreaded.buffered(source)
    }

    private class SpinLockBufferedIterator<T : Any>(
        private val source: Iterator<T>,
    ): TaskRunner.BufferedIterator<T> {

        @Volatile
        private var alive = true

        // in case a failure occurred, we re-throw it for every reader
        @Volatile
        private var error: Throwable? = null

        // we buffer up to 32 elements - we claim these per reader using our state below
        private val buffer = Array<Any?>(32) { null }
        // state tracking which slots are claimed by a reader
        private val read = AtomicInteger(0)
        // state tracking which slots are occupied by the writer
        private val write = AtomicInteger(0)

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
                // we wait until we get a slot we can fill up
                while (alive && write.get() == Int.MAX_VALUE) {
                    spinLoopHint()
                }
                if (!alive) {
                    return
                }
                // we find an index we can occupy
                val i = write.get().inv().takeLowestOneBit().countTrailingZeroBits()
                // we get the next element, or the fact that we're EOF
                if (source.hasNext()) {
                    runCatching {
                        source.next()
                    }.fold(
                        onSuccess = { value ->
                            buffer[i] = value
                            // we mark this slot now as occupied using CAS
                            var available = write.get()
                            while (!this.write.compareAndSet(available, available or 1 shl i)) {
                                spinLoopHint()
                                available = this.write.get()
                            }
                        },
                        onFailure = { exception ->
                            // we want the consumer to 'receive' the exception, so we keep the exception,
                            //  so all consumers become aware, and terminate
                            this.error = exception
                            // because we no longer mark ourselves 'alive', the fact that we didn't mark it as
                            //  available is not a problem - consumers are also exiting their spin loop
                            alive = false
                            return
                        }
                    )
                }
            }
        }

        override fun getNext(): T? {
            // we claim an available index to read: written to but not yet being read from
            var read = this.read.get()
            var write = this.write.get()
            var i = write and read.inv()
            while (!this.read.compareAndSet(read, read or 1 shl i)) {
                spinLoopHint()
                if (!alive) {
                    // we failed to acquire the index, and we're no longer alive:
                    //  either the producer reached the end or there's no more input
                    val err = this.error
                    if (err != null) {
                        throw err
                    }
                    return null
                }
                // we're still alive but are being read concurrently
                read = this.read.get()
                write = this.write.get()
                i = write and read.inv()
            }
            // we claimed an index; we get its result and mark it available for writing again
            // followed by marking its slot available for reading again as well, as we got our result
            val next = buffer[i]
            // we put its index back to 0
            val mask = (1 shl i).inv()
            write = this.write.get()
            while (!this.write.compareAndSet(write, write and mask)) {
                spinLoopHint()
                write = this.write.get()
            }
            read = this.read.get()
            while (!this.read.compareAndSet(read, read and mask)) {
                spinLoopHint()
                read = this.read.get()
            }
            // and we can return the result
            @Suppress("UNCHECKED_CAST")
            return next as T
        }

        override fun close() {
            alive = false
            // we don't close the iterator here, this is the responsibility of the producer thread, as we don't have
            //  concurrency guarantees of source iterator
        }

    }
}
