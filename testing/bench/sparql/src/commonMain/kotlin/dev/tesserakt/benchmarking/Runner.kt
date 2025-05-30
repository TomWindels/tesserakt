package dev.tesserakt.benchmarking

import dev.tesserakt.benchmarking.report.RunReporter
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class Runner(
    private val evaluation: RunnerEvaluation,
    private val reporter: RunReporter,
    private val warmupRounds: Int = 1,
    private val executionRounds: Int = 10,
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
            reporter.onStageChanged(EvaluationStage.WARMUP)
            output.markStart("warmup")
            repeat(warmupRounds) {
                EvaluatorFactory.createEvaluator(evaluation).use { evaluator ->
                    output.reset()
                    evaluation.diffs.forEach { delta ->
                        evaluator.prepare(delta)
                        evaluator.eval()
                        evaluator.finish()
                    }
                }
                reporter.onStageProgressed(it.toFloat() / warmupRounds)
            }
            output.markEnd("warmup")
            reporter.onStageChanged(EvaluationStage.EVALUATION)
            coroutineContext.ensureActive()
            // actual execution
            repeat(executionRounds) { runIndex ->
                EvaluatorFactory.createEvaluator(evaluation).use { evaluator ->
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
                reporter.onStageProgressed(runIndex.toFloat() / executionRounds)
            }
        }
    }

}
