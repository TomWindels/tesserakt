package dev.tesserakt.benchmarking.execution.update

import dev.tesserakt.benchmarking.Endpoint
import dev.tesserakt.benchmarking.EvaluationConfig
import dev.tesserakt.benchmarking.basename

data class UpdateEvaluationConfig(
    val query: String,
    val updateFilePath: String,
    override val warmup: EvaluationConfig.Warmup,
    override val outputDirPath: String,
    override val endpoint: Endpoint.Mutable,
) : EvaluationConfig {

    override val name = buildString {
        append(endpoint.queryUrl)
        append(", ")
        append(outputDirPath.basename())
        append(", ")
        append(updateFilePath.basename())
    }

    override fun metadata(): String = buildString {
        appendLine("endpoint: $endpoint")
        appendLine("updateFilePath: $updateFilePath")
        appendLine("outputDirPath: $outputDirPath")
        appendLine("warmup:")
        appendLine(warmup)
        append("query: $query")
    }

}
