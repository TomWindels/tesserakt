package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.*
import dev.tesserakt.benchmarking.execution.BenchmarkRunnerHost
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.trig.serialization.TriG
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class ReplayRunner(
    private val evaluation: ReplayRunnerEvaluation,
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
        val benchmark = ReplayBenchmark
            .from(serializer(TriG).deserialize(FileDataSource(evaluation.inputFilePath)).toStore())
            // currently only expecting a single benchmark to be present inside the file; whilst it could have multiple
            //  queries in its definition, we only care about the one in the evaluation we have available
            .single()
        // ensuring the endpoint behaviour is correct:
        // as we require full control of the endpoint's state over time, we need to be sure it's initial value
        //  is empty too
        EndpointImplementation.REQUIRE_EMPTY_INITIAL_STATE = true
        // putting the store's diffs in memory
        // actually executing it
        coroutineContext.ensureActive()
        // actual execution
        EvaluatorFactory.createEvaluatorPreferIncremental(evaluation).use { evaluator ->
            output.reset()
            val diffs = benchmark.store.diffs.toList()
            diffs.forEachIndexed { di, delta ->
                val id = RunId(deltaIndex = di)
                val prep = "${id.id()}-prep"
                reporter.onStageChanged(EvaluationStage.PREPARATION)
                output.markStart(prep)
                evaluator.prepare(delta)
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