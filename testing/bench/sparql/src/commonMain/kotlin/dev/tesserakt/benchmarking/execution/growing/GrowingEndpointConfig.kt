package dev.tesserakt.benchmarking.execution.growing

import dev.tesserakt.benchmarking.EvaluatorId

data class GrowingEndpointConfig(
    val queries: Iterable<String>,
    val insertionFilePaths: List<String>,
    val outputDirPath: String,
    val endpoint: EvaluatorId.Endpoint.Mutable,
) {

    fun toRunnerEvaluations(): List<GrowingRunnerEvaluation> = queries.mapIndexed { i, query ->
        GrowingRunnerEvaluation(
            name = "${outputDirPath.substringAfterLast('/')}-$i",
            insertionFilePaths = insertionFilePaths,
            outputDirPath = outputDirPath,
            evaluatorId = endpoint,
            query = query,
        )
    }

}
