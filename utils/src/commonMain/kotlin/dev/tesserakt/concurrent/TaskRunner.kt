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
        private value class BufferedIteratorImpl<T : Any>(val iter: Iterator<T>): BufferedIterator<T> {

            override fun supportsConcurrentAccess(): Boolean {
                // not possible as we check `hasNext()` and get `next()` non-atomically
                return false
            }

            override fun getNext(): T? {
                if (iter.hasNext()) {
                    return iter.next()
                }
                return null
            }

            override fun close() {
                // nothing to do
            }

        }

        override fun <T> dispatch(task: () -> T): TaskResult<T> {
            // we cannot dispatch the task to any runner that could buffer the results in a meaningful way
            return TaskResultImpl(value = runCatching { task() })
        }

        override fun <T : Any> buffered(source: Iterator<T>): BufferedIterator<T> {
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
     * A special [Iterator]-like type that may use background tasks to buffer results. To make sure background resources
     *  are cleaned up properly, [close] has to be called when the iterator results are no longer required.
     *
     * Unlike iterators, however, [getNext] is used to yield *a* new item, which is `null` in case the end has been
     *  reached. Note that an implementation may **not** preserve element order!
     *
     * This is semantically different compared to regular iterators as this API allows for concurrent access: the single
     *  method can be implemented atomically, as there is no [Iterator.hasNext] & [Iterator.next] method chain.
     */
    interface BufferedIterator<T : Any>: AutoCloseable {

        /**
         * Indicates whether the implementation supports concurrent access (which is possible if multiple elements are
         *  buffered in a structure that allows multiple readers)
         */
        fun supportsConcurrentAccess(): Boolean

        /**
         * Waits until an element is available, giving back the result, or `null` if the end was reached.
         */
        fun getNext(): T?

    }

    fun <T> dispatch(task: () -> T): TaskResult<T>

    /**
     * Buffers the [source] iterator into a buffer, allowing the source and sink to be executed concurrently,
     *  if possible. See [BufferedIterator] for more details.
     *
     * Note that the returned value has to be [AutoCloseable.close] after use (happens automatically if exhausted).
     */
    fun <T : Any> buffered(source: Iterator<T>): BufferedIterator<T>

}
