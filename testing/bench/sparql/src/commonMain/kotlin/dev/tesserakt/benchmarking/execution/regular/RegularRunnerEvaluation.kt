package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.execution.Evaluation
import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.rdf.types.Store

data class RegularRunnerEvaluation(
    override val name: String,
    val inputFilePath: String,
    override val outputDirPath: String,
    override val evaluatorName: String,
    val store: Store,
    override val query: String,
    val warmupRounds: Int,
    val executionRounds: Int,
) : Evaluation() {

    override fun metadata() = buildString {
        append("input: ")
        append(inputFilePath)
        append("\nevaluator: ")
        append(evaluatorName)
        append("\nquery: ")
        append(query)
        append("\nstore size: ")
        append(store.size)
    }

    fun createRunner() = RegularRunner(evaluation = this, reporter = CliRunReporter(this))

}
