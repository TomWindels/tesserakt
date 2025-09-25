package dev.tesserakt.benchmarking.execution.growing

import dev.tesserakt.benchmarking.EvaluatorId

data class GrowingRunnerConfig(
    val queries: Iterable<String>,
    val insertionFilePaths: List<String>,
    val outputDirPath: String,
    val evaluatorId: EvaluatorId,
) {

    fun toRunnerEvaluations(): List<GrowingRunnerEvaluation> = queries.mapIndexed { i, query ->
        GrowingRunnerEvaluation(
            name = "query_$i",
            insertionFilePaths = insertionFilePaths,
            outputDirPath = "${outputDirPath}/query_$i/",
            evaluatorId = evaluatorId,
            query = query,
        )
    }

}
