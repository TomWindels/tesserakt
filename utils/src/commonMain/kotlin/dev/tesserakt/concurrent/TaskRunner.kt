package dev.tesserakt.concurrent

import kotlin.jvm.JvmInline

interface TaskRunner {

    object SingleThreaded : TaskRunner {

        /**
         * A simple, platform-independent, implementation of the [TaskResult] type that can only represent the
         *  successful case
         */
        @JvmInline
        private value class TaskResultImpl<T>(val value: Result<T>): TaskResult<T> {

            override fun await(): Result<T> {
                return value
            }

        }

        @JvmInline
        private value class BufferedIteratorImpl<T>(val iter: Iterator<T>): BufferedIterator<T> {

            override fun hasNext(): Boolean {
                return iter.hasNext()
            }

            override fun next(): T {
                return iter.next()
            }

            override fun close() {
                // nothing to do
            }

        }

        override fun <T> dispatch(task: () -> T): TaskResult<T> {
            // we cannot dispatch the task to any runner that could buffer the results in a meaningful way
            return TaskResultImpl(value = runCatching { task() })
        }

        override fun <T> buffered(
            source: Iterator<T>,
            capacity: Int,
        ): BufferedIterator<T> {
            // we cannot dispatch the task to any runner that could buffer the results in a meaningful way
            return BufferedIteratorImpl(source)
        }
    }

    interface TaskResult<T> {

        /**
         * Blocks until the result can be obtained. Can be called multiple times (in which case it will yield the same
         *  value every time)
         */
        fun await(): Result<T>

    }

    /**
     * A special [Iterator] that uses background tasks to buffer results. To make sure background resources are cleaned
     *  up properly, [close] has to be called when the iterator results are no longer required.
     */
    interface BufferedIterator<T>: Iterator<T>, AutoCloseable

    fun <T> dispatch(task: () -> T): TaskResult<T>

    /**
     * Buffers the [source] iterator into a buffer (of [capacity] size, which has to be a power of two!), allowing the
     *  source and sink to be executed concurrently, if possible.
     */
    fun <T> buffered(source: Iterator<T>, capacity: Int = 1024): BufferedIterator<T>

}
