package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.execution.Evaluation

private val fs = js("require('fs')")

actual fun writeMetadata(directory: String, evaluation: Evaluation) {
    fs.writeFileSync(directory + "metadata", evaluation.metadata())
}
