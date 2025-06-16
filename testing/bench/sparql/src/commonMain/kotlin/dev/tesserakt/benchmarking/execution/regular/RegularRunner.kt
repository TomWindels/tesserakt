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
        // putting the store's diffs in memory
        // actually executing it
        output.create()
        output.use {
            // warmup
            EvaluatorFactory.createEvaluatorPreferRegular(evaluation).use { evaluator ->
                output.markStart("initialisation")
                evaluator.prepare(SnapshotStore.Diff(insertions = evaluation.store, deletions = emptyStore()))
                output.markEnd("initialisation")
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