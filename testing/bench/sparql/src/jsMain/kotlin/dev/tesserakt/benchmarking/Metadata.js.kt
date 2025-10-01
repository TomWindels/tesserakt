package dev.tesserakt.benchmarking

private val fs = js("require('fs')")

actual fun writeMetadata(directory: String, evaluation: EvaluationConfig) {
    fs.writeFileSync(directory + "metadata", evaluation.metadata())
}
