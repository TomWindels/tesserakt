package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.serialization.common.Path
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

class Runner(
    private val config: RunnerConfig
) {

    private val source = ReplayBenchmark.from(TriGSerializer.deserialize(Path(config.inputFilePath)).consume()).single()
    private val output = OutputWriter(config)

    fun run() {
        // putting the store's diffs in memory
        val deltas = source.store.diffs.toList()
        // actually executing it
        output.create()
        output.use {
            // warmup
            output.markStart("warmup")
            source.queries.forEach { query ->
                val evaluator = if (config.referenceImplementation) Evaluator.reference(query) else Evaluator.self(query)
                output.reset()
                deltas.forEach { delta ->
                    evaluator.prepare(delta)
                    evaluator.eval()
                    evaluator.finish()
                }
            }
            output.markEnd("warmup")
            // actual execution
            repeat(10) { runIndex ->
                source.queries.forEachIndexed { qi, query ->
                    val evaluator = if (config.referenceImplementation) Evaluator.reference(query) else Evaluator.self(query)
                    output.reset()
                    deltas.forEachIndexed { di, delta ->
                        val id = RunId(queryIndex = qi, deltaIndex = di, runIndex = runIndex)
                        evaluator.prepare(delta)
                        output.markStart(id.id())
                        evaluator.eval()
                        output.markEnd(id.id())
                        val outputs = evaluator.finish()
                        output.markOutputs(id.id(), outputs)
                    }
                }
            }
        }
    }

}
