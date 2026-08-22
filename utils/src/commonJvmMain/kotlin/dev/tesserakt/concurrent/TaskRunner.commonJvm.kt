package dev.tesserakt.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

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

    override fun <T> buffered(
        source: Iterator<T>,
        capacity: Int,
    ): TaskRunner.BufferedIterator<T> {
        val iterator = SpinLockBufferedIterator(source, capacity)
        // we need to make sure the loop is actually producing data; if there's no threads available, we need to fall
        //  back to a single threaded variant to prevent a deadlock on a resource that never comes
        var started = false
        var beginProducing = false
        val task = executor.submit {
            started = true
            // we need to make sure this iterator is actually getting used; we wait until we get the go-ahead
            var remaining = 100_000
            while (!beginProducing && --remaining > 0);
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

    // sentinel object used to mark the source as exhausted
    private object Done

    private class SpinLockBufferedIterator<T>(
        private val source: Iterator<T>,
        capacity: Int,
    ): TaskRunner.BufferedIterator<T> {

        @Volatile
        private var alive = true
        // allowing `null` support by using custom objects as state indicators
        private val buffer = Array<Any?>(capacity) { null }
        private val mask = buffer.size - 1

        // modified by `next()`
        @Volatile
        private var head = 0

        // modified by `producerLoop()`
        @Volatile
        private var tail = 0

        init {
            check(capacity.countOneBits() == 1) { "Invalid capacity provided: expected a power of 2!" }
        }

        /**
         * The method that is run as long as there are new values that can be obtained, and is still being processed
         *  by the receiver.
         */
        fun producerLoop() {
            while (alive) {
                // we can fill between [tail .. head - 1]
                // we are the only source that mutates the tail, so we can keep it local here
                val pos = tail
                val next = (tail + 1) and mask
                // we now wait until the head position has moved so that our writing target is available
                while (alive && pos == ((head - 1) and mask));
                if (!alive) {
                    return
                }
                // we update the tail regardless, so that the `Done` slot also comes into the reader's range
                if (source.hasNext()) {
                    runCatching {
                        source.next()
                    }.fold(
                        onSuccess = { value ->
                            buffer[pos] = value
                            tail = next
                        },
                        onFailure = { exception ->
                            // we want the consumer to 'receive' the exception, so we put the exception in there,
                            //  and terminate ourselves
                            buffer[pos] = exception
                            tail = next
                            alive = false
                            return
                        }
                    )
                } else {
                    buffer[pos] = Done
                    tail = next
                    alive = false
                    return
                }
            }
        }

        override fun hasNext(): Boolean {
            // we have to wait until the state advances
            while (alive && head == tail);
            // if it has advanced to something other than 'done', we know there's at least this next item
            //  to yield
            return alive && head != tail && buffer[head] !== Done
        }

        override fun next(): T {
            // we have to wait until the state advances
            while (alive && head == tail);
            val next = buffer[head]
            // next is either
            // * 'T' in the typical case
            // * 'Pending' (but we made sure it wasn't, and only we change it if it is an element instance)
            // * 'Done' if we reached the end
            if (next === Done) {
                throw NoSuchElementException()
            }
            if (next is Throwable) {
                // we need to terminate early, and do not advance the head position, as the producer ended in failure
                throw next
            }
            // advancing the head, so that the task can reuse this slot
            head = (head + 1) and mask
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
