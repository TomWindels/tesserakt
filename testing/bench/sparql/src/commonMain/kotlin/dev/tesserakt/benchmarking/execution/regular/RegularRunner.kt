package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.*
import dev.tesserakt.benchmarking.report.RunReporter
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.factory.emptyStore
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class RegularRunner(
    private val evaluation: RegularRunnerEvaluation,
    private val reporter: RunReporter,
) {

    suspend fun run() {
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
        val output = OutputWriter(evaluation)
        // ensuring the endpoint behaviour is correct:
        // * if we have a data store we want to evaluate (!= null), we require the initial state of (external)
        //  stores (= endpoints) to be empty
        // * otherwise, we might be testing against data already present inside the endpoint, and thus should not
        //  expect empty responses upon executing any query (so the validation should be skipped)
        EndpointImplementation.REQUIRE_EMPTY_INITIAL_STATE = evaluation.store != null
        // actually executing it
        output.create()
        output.use {
            // warmup
            EvaluatorFactory.createEvaluatorPreferRegular(evaluation).use { evaluator ->
                if (evaluation.store != null) {
                    output.markStart("initialisation")
                    evaluator.prepare(SnapshotStore.Diff(insertions = evaluation.store, deletions = emptyStore()))
                    output.markEnd("initialisation")
                }
                reporter.onStageChanged(EvaluationStage.WARMUP)
                output.markStart("warmup")
                repeat(evaluation.warmupRounds) {
                    output.reset()
                    evaluator.eval()
                    evaluator.finish()
                    reporter.onStageProgressed(it.toFloat() / evaluation.warmupRounds)
                    RunContext.onIterationFinished()
                }
                output.markEnd("warmup")
                reporter.onStageChanged(EvaluationStage.EVALUATION)
                coroutineContext.ensureActive()
                // actual execution
                repeat(evaluation.executionRounds) { runIndex ->
                    output.reset()
                    val id = RunId(deltaIndex = 0, runIndex = runIndex)
                    output.markStart(id.id())
                    evaluator.eval()
                    output.markEnd(id.id())
                    val outputs = evaluator.finish()
                    output.markOutputs(id.id(), outputs)
                    coroutineContext.ensureActive()
                    reporter.onStageProgressed(runIndex.toFloat() / evaluation.executionRounds)
                    RunContext.onIterationFinished()
                }
            }
        }
    }

}