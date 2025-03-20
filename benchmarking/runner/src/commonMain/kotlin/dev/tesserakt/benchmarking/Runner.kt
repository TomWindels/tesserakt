package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.serialization.common.Path
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

class Runner(
    inputFilePath: String,
    outputDirPath: String,
    private val referenceImplementation: Boolean
) {

    private val source = ReplayBenchmark.from(TriGSerializer.deserialize(Path(inputFilePath)).consume()).single()
    private val output = OutputWriter(outputDirPath)

    fun run() {
        // putting the store's diffs in memory
        val deltas = source.store.diffs.toList()
        // actually executing it
        output.create()
        output.use {
            // warmup
            output.markStart("warmup")
            source.queries.forEach { query ->
                val evaluator = if (referenceImplementation) Evaluator.reference(query) else Evaluator.self(query)
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
                    val evaluator = if (referenceImplementation) Evaluator.reference(query) else Evaluator.self(query)
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

    companion object {

        /**
         * Creating a Runner instantiated from the command line, formatted as
         * ```
         * runner [-i/--input] path/to/benchmark.ttl [-o/--output] [path/to/output_dir] [--use-reference-implementation]
         * ```
         * or
         * ```
         * runner -o/--output path/to/output_dir path/to/benchmark.ttl
         * ```
         * etc.
         */
        operator fun invoke(args: Array<String>): Runner {
            var input: String? = null
            var output: String? = null
            var i = 0
            var useReference = false
            while (i < args.size) {
                when {
                    args[i].isInputFlag() -> {
                        ++i
                        if (i >= args.size) {
                            throw IllegalArgumentException("No input filepath provided!")
                        } else {
                            input = args[i]
                        }
                    }
                    args[i].isOutputFlag() -> {
                        ++i
                        if (i>= args.size) {
                            throw IllegalArgumentException("No output filepath provided!")
                        } else {
                            output = args[i]
                        }
                    }
                    args[i].isReferenceImplementationFlag() -> {
                        useReference = true
                    }
                    input == null -> {
                        input = args[i]
                    }
                    output == null -> {
                        output = args[i]
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid command line structure!")
                    }
                }
                ++i
            }
            if (input == null) {
                throw IllegalArgumentException("No input filepath provided!")
            }
            if (!input.endsWith(".ttl")) {
                throw IllegalArgumentException("Invalid input filepath! `.ttl` file expected!")
            }
            if (output != null && output.last() != '/') {
                throw IllegalArgumentException("Invalid output path! Directory expected!")
            }
            return Runner(
                inputFilePath = input,
                outputDirPath = output ?: input.createOutputFilepath(),
                referenceImplementation = useReference
            )
        }

        private fun String.isInputFlag() = this == "-i" || this == "--input"

        private fun String.isOutputFlag() = this == "-o" || this == "--output"

        private fun String.isReferenceImplementationFlag() = this == "--use-reference-implementation"

        private fun String.createOutputFilepath() = this.dropLast(3) + "_${currentEpochMs()}/"

    }

}
