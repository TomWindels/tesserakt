package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.*
import dev.tesserakt.benchmarking.report.RunReporter
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class ReplayRunner(
    private val evaluation: ReplayRunnerEvaluation,
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
        // as we require full control of the endpoint's state over time, we need to be sure it's initial value
        //  is empty too
        EndpointImplementation.REQUIRE_EMPTY_INITIAL_STATE = true
        // putting the store's diffs in memory
        // actually executing it
        output.create()
        output.use {
            // warmup
            reporter.onStageChanged(EvaluationStage.WARMUP)
            output.markStart("warmup")
            repeat(evaluation.warmupRounds) {
                EvaluatorFactory.createEvaluatorPreferIncremental(evaluation).use { evaluator ->
                    output.reset()
                    evaluation.diffs.forEach { delta ->
                        evaluator.prepare(delta)
                        evaluator.eval()
                        evaluator.finish()
                    }
                }
                reporter.onStageProgressed(it.toFloat() / evaluation.warmupRounds)
                RunContext.onIterationFinished()
            }
            output.markEnd("warmup")
            reporter.onStageChanged(EvaluationStage.EVALUATION)
            coroutineContext.ensureActive()
            // actual execution
            repeat(evaluation.executionRounds) { runIndex ->
                EvaluatorFactory.createEvaluatorPreferIncremental(evaluation).use { evaluator ->
                    output.reset()
                    evaluation.diffs.forEachIndexed { di, delta ->
                        val id = RunId(deltaIndex = di, runIndex = runIndex)
                        val prep = "${id.id()}-prep"
                        output.markStart(prep)
                        evaluator.prepare(delta)
                        output.markEnd(prep)
                        output.markStart(id.id())
                        evaluator.eval()
                        output.markEnd(id.id())
                        val outputs = evaluator.finish()
                        output.markOutputs(id.id(), outputs)
                        coroutineContext.ensureActive()
                    }
                }
                reporter.onStageProgressed(runIndex.toFloat() / evaluation.executionRounds)
                RunContext.onIterationFinished()
            }
        }
    }

}