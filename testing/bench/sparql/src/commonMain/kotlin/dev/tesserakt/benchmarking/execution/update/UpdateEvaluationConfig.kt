package dev.tesserakt.benchmarking.execution.update

import dev.tesserakt.benchmarking.Endpoint
import dev.tesserakt.benchmarking.EvaluationConfig

data class UpdateEvaluationConfig(
    val query: String,
    val warmupQueries: List<String>,
    val warmupRuns: Int,
    val updateFilePath: String,
    override val outputDirPath: String,
    override val endpoint: Endpoint.Mutable,
) : EvaluationConfig {

    override val name = buildString {
        append(endpoint.queryUrl)
        append(", ")
        append(outputDirPath.substringAfterLast('/'))
        append(", ")
        append(updateFilePath.substringAfterLast('/'))
    }

    override fun metadata(): String = buildString {
        appendLine("endpoint: $endpoint")
        appendLine("updateFilePath: $updateFilePath")
        appendLine("outputDirPath: $outputDirPath")
        appendLine("warmupRuns: $warmupRuns")
        appendLine("warmupQueries:\n${warmupQueries.joinToString("\n\n")}")
        append("query: $query")
    }

}
