package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.execution.Evaluation
import java.io.File

private val version by lazy {
    try {
        CommandExecutor.run("git rev-parse HEAD")
    } catch (_: Throwable) {
        "version information unavailable"
    }
}

actual fun writeMetadata(directory: String, evaluation: Evaluation) {
    File(directory + "metadata").writeText(buildString {
        append("version: ")
        append(version)
        append("\n")
        append(evaluation.metadata())
    })
}
