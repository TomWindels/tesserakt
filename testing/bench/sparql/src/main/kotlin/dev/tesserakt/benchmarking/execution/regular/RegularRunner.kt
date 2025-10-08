package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.EvaluationStage
import dev.tesserakt.benchmarking.RunId
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

class RegularRunner(
    private val evaluation: RegularEvaluationConfig,
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
        // ensuring we have the initially required data in memory - if any
        val store = evaluation
            .inputFilePath
            ?.let { TriGSerializer.deserialize(FileDataSource(it)).consume() }
            ?: emptyStore()
        reporter.onStageChanged(EvaluationStage.WARMUP)
        doWarmup(endpoint = evaluation.endpoint, warmup = evaluation.warmup)
        // ensuring the endpoint behaviour is correct:
        // * if we have a data store we want to evaluate (!= null), we require the initial state of (external)
        //  stores (= endpoints) to be empty
        // * otherwise, we might be testing against data already present inside the endpoint, and thus should not
        //  expect empty responses upon executing any query (so the validation should be skipped)
        EndpointEvaluator.REQUIRE_EMPTY_INITIAL_STATE = store.isNotEmpty()
        evaluation.endpoint.toRunner(evaluation.query).use { evaluator ->
            if (store.isNotEmpty()) {
                reporter.onStageChanged(EvaluationStage.PREPARATION)
                output.markStart("preparation")
                evaluator.prepare(SnapshotStore.Diff(insertions = store, deletions = emptyStore()))
                output.markEnd("preparation")
            }
            reporter.onStageChanged(EvaluationStage.EVALUATION)
            coroutineContext.ensureActive()
            val id = RunId(deltaIndex = 0)
            output.markStart(id.id())
            evaluator.eval()
            output.markEnd(id.id())
            val outputs = evaluator.finish()
            output.markOutputs(id.id(), outputs)
            coroutineContext.ensureActive()
        }
    }

}