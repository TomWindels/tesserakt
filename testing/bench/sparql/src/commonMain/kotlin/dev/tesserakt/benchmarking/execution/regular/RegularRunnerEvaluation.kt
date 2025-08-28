package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.EvaluatorId
import dev.tesserakt.benchmarking.execution.Evaluation

data class RegularRunnerEvaluation(
    override val name: String,
    val inputFilePath: String?,
    override val outputDirPath: String,
    override val evaluatorId: EvaluatorId,
    override val query: String,
) : Evaluation() {

    override fun metadata() = buildString {
        append("input: ")
        append(inputFilePath)
        append("\nevaluator: ")
        append(evaluatorId)
        append("\nquery: ")
        append(query)
    }

    override fun withIndex(index: Int): RegularRunnerEvaluation {
        return copy(
            name = "$name #${index}",
            outputDirPath = "$outputDirPath$index/"
        )
    }

}
