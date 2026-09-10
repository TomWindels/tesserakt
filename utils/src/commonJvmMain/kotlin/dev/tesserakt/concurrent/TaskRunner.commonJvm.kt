package dev.tesserakt.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor

class ThreadedTaskRunner private constructor(
    private val executor: ExecutorService,
    val threadCount: Int,
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

    /**
     * Identical to [parallelize], with the guarantee that the calling thread is not blocked
     */
    fun parallelizeInBackground(maxCount: Int = Int.MAX_VALUE, block: () -> Unit): TaskRunner.TaskResult<Unit> {
        if (maxCount <= 0) {
            throw IllegalArgumentException("At least one worker is required, got ${maxCount}!")
        }
        // we can't count ourselves as a background worker
        val backgroundWorkers = (threadCount - 1).coerceAtMost(maxCount)
        if (backgroundWorkers <= 0) {
            return TaskRunner.SingleThreaded.TaskResultImpl(
                value = Result.failure(IllegalStateException("Found no background workers to do execution with!"))
            )
        }
        val dispatchedResults = List(backgroundWorkers) { dispatch(block) }
        return FutureCollectionResult(
            // we both dispatch our background workers, as well as the calling thread,
            //  so we get some extra parallelization going without scheduling overhead
            inner = dispatchedResults
        )
    }

    override fun <T : Any> buffered(source: Iterator<T>): TaskRunner.BufferedIterator<T> {
        val iterator = SpinLoopBufferedIterator(source)
        executor.submit {
            iterator.producerLoop()
            // we reached here, so we can safely close the input source if necessary
            if (source is AutoCloseable) {
                source.close()
            }
        }
        return iterator
    }

    internal class SpinLoopBufferedIterator<T : Any>(
        private val source: Iterator<T>,
    ): TaskRunner.BufferedIterator<T> {

        private val buf = SPMCBuffer<T>()

        /**
         * The method that is run as long as there are new values that can be obtained, and is still being processed
         *  by the receiver.
         */
        fun producerLoop() {
            while (buf.alive) {
                // we get the next element, or the fact that we're EOF
                if (source.hasNext()) {
                    runCatching {
                        source.next()
                    }.fold(
                        onSuccess = { value -> buf.push(value) },
                        onFailure = { exception ->
                            buf.close(exception)
                            return
                        }
                    )
                } else {
                    buf.close()
                    return
                }
            }
        }

        override fun supportsConcurrentAccess(): Boolean {
            return true
        }

        override fun getNext(): T? {
            return buf.poll()
        }

        override fun close() {
            buf.close()
        }

    }

}
