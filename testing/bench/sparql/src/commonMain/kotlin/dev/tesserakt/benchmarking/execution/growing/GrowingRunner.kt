package dev.tesserakt.benchmarking.execution.growing

import dev.tesserakt.benchmarking.*
import dev.tesserakt.benchmarking.execution.BenchmarkRunnerHost
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.rdf.types.factory.emptyStore
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class GrowingRunner(
    private val evaluation: GrowingRunnerEvaluation,
): BenchmarkRunnerHost.Runner() {

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
        // whilst we are responsible for 'growing' the dataset, we're not responsible for its initial state
        EndpointImplementation.REQUIRE_EMPTY_INITIAL_STATE = false
        // putting the store's diffs in memory
        // actually executing it
        coroutineContext.ensureActive()
        // actual execution
        EvaluatorFactory.createEvaluatorPreferIncremental(evaluation).use { evaluator ->
            output.reset()
            evaluation.insertionFilePaths.forEachIndexed { di, insertionFilePath ->
                val insertion = TriGSerializer
                    .deserialize(FileDataSource(insertionFilePath))
                    .consume()
                val id = RunId(deltaIndex = di)
                val prep = "${id.id()}-prep"
                reporter.onStageChanged(EvaluationStage.PREPARATION)
                output.markStart(prep)
                evaluator.prepare(SnapshotStore.Diff(insertions = insertion, deletions = emptyStore()))
                output.markEnd(prep)
                reporter.onStageChanged(EvaluationStage.EVALUATION)
                output.markStart(id.id())
                evaluator.eval()
                output.markEnd(id.id())
                val outputs = evaluator.finish()
                output.markOutputs(id.id(), outputs)
                coroutineContext.ensureActive()
            }
        }
        RunContext.onIterationFinished()
    }

}