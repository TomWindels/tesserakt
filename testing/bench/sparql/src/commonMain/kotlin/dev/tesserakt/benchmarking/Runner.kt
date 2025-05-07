package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class Runner(
    private val config: RunnerConfig
) {

    private val source = ReplayBenchmark.from(TriGSerializer.deserialize(FileDataSource(config.inputFilePath)).toStore()).single()
    private val output = OutputWriter(config)

    suspend fun run() {
        // putting the store's diffs in memory
        val deltas = source.store.diffs.toList()
        // actually executing it
        output.create()
        output.use {
            // warmup
            output.markStart("warmup")
            source.queries.forEach { query ->
                config.createEvaluator(query).use { evaluator ->
                    output.reset()
                    deltas.forEach { delta ->
                        evaluator.prepare(delta)
                        evaluator.eval()
                        evaluator.finish()
                    }
                }
            }
            output.markEnd("warmup")
            coroutineContext.ensureActive()
            // actual execution
            repeat(10) { runIndex ->
                source.queries.forEachIndexed { qi, query ->
                    config.createEvaluator(query).use { evaluator ->
                        output.reset()
                        deltas.forEachIndexed { di, delta ->
                            val id = RunId(queryIndex = qi, deltaIndex = di, runIndex = runIndex)
                            evaluator.prepare(delta)
                            output.markStart(id.id())
                            evaluator.eval()
                            output.markEnd(id.id())
                            val outputs = evaluator.finish()
                            output.markOutputs(id.id(), outputs)
                            coroutineContext.ensureActive()
                        }
                    }
                }
            }
        }
    }

}
