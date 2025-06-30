package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.execution.Evaluation

data class ReplayRunnerEvaluation(
    override val name: String,
    val inputFilePath: String,
    override val outputDirPath: String,
    override val evaluatorName: String,
    override val query: String,
) : Evaluation() {

    override fun metadata() = buildString {
        append("input: ")
        append(inputFilePath)
        append("\nevaluator: ")
        append(evaluatorName)
        append("\nquery: ")
        append(query)
    }

    override fun withIndex(index: Int): ReplayRunnerEvaluation {
        return copy(
            name = "$name-${index}",
            outputDirPath = outputDirPath.replace(name, "$name/${index}")
        )
    }

}