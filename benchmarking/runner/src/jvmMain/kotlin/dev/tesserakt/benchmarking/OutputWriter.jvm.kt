package dev.tesserakt.benchmarking

import java.io.Closeable
import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class OutputWriter actual constructor(directory: String): Closeable {

    private val memoryObserver: MemoryObserver
    private val timeObserver: TimeObserver

    init {
        check(directory.endsWith('/'))
        val root = File(directory)
        root.mkdir()
        val contents = root.list()
        when {
            contents == null -> throw IllegalArgumentException("Path `$directory` is not a directory!")
            contents.isNotEmpty() -> throw IllegalArgumentException("Path `$directory` is not empty!")
        }
        memoryObserver = MemoryObserver(directory + "memory.csv")
        timeObserver = TimeObserver(directory + "time.csv")
    }

    /**
     * Called when the benchmark has been started, just before the very first call to [markStart]
     */
    actual fun create() {
        // starting the periodic memory observations
        memoryObserver.start()
    }

    /**
     * Called on every new run start
     */
    actual fun reset() {
        memoryObserver.reset()
    }

    /**
     * Called when the benchmark has finished, just after the very last call to [markEnd]
     */
    actual override fun close() {
        memoryObserver.stop()
        timeObserver.stop()
    }

    /**
     * Notification about the start of an execution for the given [id]
     */
    actual fun markStart(id: String) {
        timeObserver.start(id)
    }

    /**
     * Notification about the end of an execution for the given [id]
     */
    actual fun markEnd(id: String) {
        timeObserver.end(id)
    }

}