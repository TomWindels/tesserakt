package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.endpoint.EndpointEvaluator
import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class OutputWriter actual constructor(evaluation: EvaluationConfig) : AutoCloseable {

    private val timeObserver: TimeObserver
    private val outputObserver: OutputObserver

    init {
        val directory = evaluation.outputDirPath
        check(directory.endsWith('/'))
        val root = File(directory)
        root.mkdirs()
        val contents = root.list()
        when {
            contents == null -> throw IllegalArgumentException("Path `$directory` is not a directory!")
            contents.isNotEmpty() -> throw IllegalArgumentException("Path `$directory` is not empty!")
        }
        timeObserver = TimeObserver(directory + "time.csv")
        outputObserver = OutputObserver(directory + "outputs.csv")
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

    actual fun markOutputs(id: String, output: EndpointEvaluator.Output) {
        outputObserver.markResult(id, output)
    }

}