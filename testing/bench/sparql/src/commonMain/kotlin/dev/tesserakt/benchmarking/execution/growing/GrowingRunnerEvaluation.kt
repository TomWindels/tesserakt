package dev.tesserakt.benchmarking.execution.growing

import dev.tesserakt.benchmarking.EvaluatorId
import dev.tesserakt.benchmarking.execution.Evaluation

data class GrowingRunnerEvaluation(
    override val name: String,
    val insertionFilePaths: List<String>,
    override val outputDirPath: String,
    override val evaluatorId: EvaluatorId,
    override val query: String,
) : Evaluation() {

    override fun metadata() = buildString {
        append("input: ")
        append(insertionFilePaths)
        append("\nevaluator: ")
        append(evaluatorId)
        append("\nquery: ")
        append(query)
    }

    override fun withIndex(index: Int): GrowingRunnerEvaluation {
        return copy(
            name = "$name #${index}",
            outputDirPath = "$outputDirPath$index/"
        )
    }

}
