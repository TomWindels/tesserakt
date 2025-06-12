package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.rdf.types.SnapshotStore

data class RunnerEvaluation(
    val name: String,
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
    val diffs: List<SnapshotStore.Diff>,
    val query: String,
    val warmupRounds: Int,
    val executionRounds: Int,
) {

    fun createRunner() = Runner(evaluation = this, reporter = CliRunReporter(this))

}
