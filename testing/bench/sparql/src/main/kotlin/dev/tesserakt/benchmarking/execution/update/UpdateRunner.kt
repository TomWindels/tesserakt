package dev.tesserakt.benchmarking.execution.update

import dev.tesserakt.benchmarking.EvaluationStage
import dev.tesserakt.benchmarking.endpoint.EndpointEvaluator
import dev.tesserakt.benchmarking.execution.Benchmark
import dev.tesserakt.benchmarking.execution.doWarmup
import dev.tesserakt.benchmarking.execution.toRunner
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.rdf.types.factory.emptyStore
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class UpdateRunner(
    private val evaluation: UpdateEvaluationConfig,
): Benchmark.Runner() {

    override suspend fun run() {
        exec().fold(
            onSuccess = {
                reporter.onStageChanged(EvaluationStage.FINISHED)
            },
            onFailure = {
                reporter.onStageFailed(it)
            }
        )
    }

    private suspend fun exec() = runCatching {
        // ensuring the endpoint behaviour is correct:
        // whilst we are responsible for updating the dataset, we're not responsible for its initial state
        EndpointEvaluator.REQUIRE_EMPTY_INITIAL_STATE = false
        // putting the store's diffs in memory
        // actually executing it
        coroutineContext.ensureActive()
        // actual execution
        // * warmup phase *
        // repeating every warmup query `warmup count` times
        reporter.onStageChanged(EvaluationStage.WARMUP)
        doWarmup(endpoint = evaluation.endpoint, warmup = evaluation.warmup)
        // * evaluation phase *
        val insertion = TriGSerializer
            .deserialize(FileDataSource(evaluation.updateFilePath))
            .consume()
        evaluation.endpoint.toRunner(evaluation.query).use { evaluator ->
            reporter.onStageChanged(EvaluationStage.EVALUATION)
            output.markStart("pre-update")
            evaluator.eval()
            output.markEnd("pre-update")
            val preUpdateResults = evaluator.finish()
            output.markOutputs("pre-update", preUpdateResults)

            reporter.onStageChanged(EvaluationStage.PREPARATION)
            output.markStart("update")
            evaluator.prepare(SnapshotStore.Diff(insertions = insertion, deletions = emptyStore()))
            output.markEnd("update")

            reporter.onStageChanged(EvaluationStage.EVALUATION)
            output.markStart("post-update")
            evaluator.eval()
            output.markEnd("post-update")
            val postUpdateResults = evaluator.finish()
            output.markOutputs("post-update", postUpdateResults)

            coroutineContext.ensureActive()
        }
    }

}