package dev.tesserakt.benchmarking.execution.growing

import dev.tesserakt.benchmarking.EvaluatorId

data class GrowingRunnerConfig(
    val queries: Iterable<String>,
    val insertionFilePaths: List<String>,
    val outputDirPath: String,
    val evaluatorId: EvaluatorId,
) {

    override fun toString() =
        "Benchmark runner\n* Inputs: ${insertionFilePaths.joinToString()}\n* Output: $outputDirPath\n* Implementation: $evaluatorId"

    val name: String
        get() = insertionFilePaths.first().substringAfterLast('/').substringBeforeLast('.')

    fun toRunnerEvaluations(): List<GrowingRunnerEvaluation> = queries.mapIndexed { i, query ->
        GrowingRunnerEvaluation(
            name = "${outputDirPath.substringAfterLast('/')}-$i",
            insertionFilePaths = insertionFilePaths,
            outputDirPath = outputDirPath,
            evaluatorId = evaluatorId,
            query = query,
        )
    }

}
