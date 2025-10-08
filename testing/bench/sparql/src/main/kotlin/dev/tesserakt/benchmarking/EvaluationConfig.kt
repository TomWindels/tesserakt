package dev.tesserakt.benchmarking

interface EvaluationConfig {

    data class Warmup(
        val queries: List<String>,
        val runs: Int,
    ) {

        override fun toString() = buildString {
            append("runs: ")
            appendLine(runs)
            append("queries:")
            queries.forEach { query ->
                append('\n')
                append(query)
            }
        }

    }

    val name: String
    val endpoint: Endpoint
    val outputDirPath: String
    val warmup: Warmup

    fun metadata(): String
}
