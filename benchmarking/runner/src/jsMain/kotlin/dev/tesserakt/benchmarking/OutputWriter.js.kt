package dev.tesserakt.benchmarking

private val fs = js("require('fs')")

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class OutputWriter actual constructor(config: RunnerConfig) : AutoCloseable {

    private val timeObserver: TimeObserver
    private val outputObserver: OutputObserver

    init {
        val directory = config.outputDirPath
        check(directory.endsWith('/'))
        if (!directory.isFolder()) {
            val opts: dynamic = Any()
            opts.recursive = true
            fs.mkdirSync(directory, opts)
        }
        if (directory.listFiles().isNotEmpty()) {
            throw IllegalArgumentException("Path `$directory` is not empty!")
        }
        timeObserver = TimeObserver(directory + "time.csv")
        outputObserver = OutputObserver(directory + "outputs.csv")
        fs.writeFileSync(
            directory + "metadata",
            "input: ${config.inputFilePath}\nevaluator: ${config.evaluatorName}"
        )
    }

    /**
     * Called when the benchmark has been started, just before the very first call to [markStart]
     */
    actual fun create() {
        /* nothing to do */
    }

    /**
     * Called on every new run start
     */
    actual fun reset() {
        /* nothing to do */
    }

    /**
     * Called when the benchmark has finished, just after the very last call to [markEnd]
     */
    actual override fun close() {
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
