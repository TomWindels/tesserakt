package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.report.CliRunReporter
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

data class RunnerEvaluation(
    val name: String,
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
    val diffs: List<SnapshotStore.Diff>,
    val query: String,
) {

    fun createRunner() = Runner(evaluation = this, reporter = CliRunReporter(this))

    companion object {

        fun from(config: RunnerConfig): List<RunnerEvaluation> {
            var i = 0
            return ReplayBenchmark
                .from(TriGSerializer.deserialize(FileDataSource(config.inputFilePath)).consume())
                .flatMap { benchmark ->
                    val diffs = benchmark.store.diffs.toList()
                    val nameBase = config.name
                    benchmark.queries.map { query ->
                        val name = "$nameBase-${++i}"
                        RunnerEvaluation(
                            name = name,
                            inputFilePath = config.inputFilePath,
                            outputDirPath = config.outputDirPath.replace(nameBase, name),
                            evaluatorName = config.evaluatorName,
                            diffs = diffs,
                            query = query,
                        )
                    }
                }
        }

        fun Iterable<RunnerConfig>.toEvaluations() = flatMap { from(it) }

        private val RunnerConfig.name: String
            get() = inputFilePath.substringAfterLast('/').substringBeforeLast('.')

    }

}
