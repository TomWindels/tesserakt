package dev.tesserakt.concurrent

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

actual sealed class ConcurrencyMode {

    internal actual abstract fun toTaskRunner(): TaskRunner

    actual companion object

}

/**
 * Enables the use of multiple threads to distribute work of larger processes, such as ingestion of large files.
 */
class MultiThreaded(val pool: ExecutorService) : ConcurrencyMode() {

    override fun toTaskRunner(): TaskRunner {
        return ThreadedTaskRunner(pool)
    }

    /**
     * Enables the use of multiple threads to distribute work of larger processes, such as ingestion of large files.
     *
     * Uses [Executors.newCachedThreadPool] containing daemon threads.
     */
    companion object: ConcurrencyMode() {

        // we make sure we reuse the pool if this default configuration instance is used at least once
        private val pool by lazy {
            Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }
        }

        override fun toTaskRunner(): TaskRunner {
            return ThreadedTaskRunner(pool)
        }

    }

}
