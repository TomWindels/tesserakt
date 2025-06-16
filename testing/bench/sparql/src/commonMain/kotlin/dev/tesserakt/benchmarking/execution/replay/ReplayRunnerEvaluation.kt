package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.execution.Evaluation
import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.rdf.types.SnapshotStore

data class ReplayRunnerEvaluation(
    override val name: String,
    val inputFilePath: String,
    override val outputDirPath: String,
    override val evaluatorName: String,
    val diffs: List<SnapshotStore.Diff>,
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
        append("\ndiff count: ")
        append(diffs.size)
        append("\ntotal insertions: ")
        append(diffs.sumOf { it.insertions.size })
        append("\ntotal deletions: ")
        append(diffs.sumOf { it.deletions.size })
    }

    fun createRunner() = ReplayRunner(evaluation = this, reporter = CliRunReporter(this))

}
