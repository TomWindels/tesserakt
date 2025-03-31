package dev.tesserakt.benchmarking

import java.io.Closeable
import java.io.File

private val version by lazy {
    CommandExecutor.run("git rev-parse HEAD")
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class OutputWriter actual constructor(config: RunnerConfig): Closeable {

    private val memoryObserver: MemoryObserver
    private val timeObserver: TimeObserver
    private val outputObserver: OutputObserver

    init {
        val directory = config.outputDirPath
        check(directory.endsWith('/'))
        val root = File(directory)
        root.mkdirs()
        val contents = root.list()
        when {
            contents == null -> throw IllegalArgumentException("Path `$directory` is not a directory!")
            contents.isNotEmpty() -> throw IllegalArgumentException("Path `$directory` is not empty!")
        }
        memoryObserver = MemoryObserver(directory + "memory.csv")
        timeObserver = TimeObserver(directory + "time.csv")
        outputObserver = OutputObserver(directory + "outputs.csv")
        File(directory + "metadata").writeText("version: $version\ninput: ${config.inputFilePath}\nevaluator: ${config.evaluatorName}")
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
        outputObserver.stop()
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

    actual fun markOutputs(id: String, output: Evaluator.Output) {
        outputObserver.markResult(id, output)
    }

}