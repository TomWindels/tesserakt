package dev.tesserakt.benchmarking

interface EvaluationConfig {
    val name: String
    val endpoint: Endpoint
    val outputDirPath: String

    fun metadata(): String
}
