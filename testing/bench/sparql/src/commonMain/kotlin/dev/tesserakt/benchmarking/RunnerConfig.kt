package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

data class RunnerConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
    val warmups: Int,
    val runs: Int,
) {

    override fun toString() =
        "Benchmark runner\n* Input: $inputFilePath\n* Output: $outputDirPath\n* Implementation: $evaluatorName"

    val name: String
        get() = inputFilePath.substringAfterLast('/').substringBeforeLast('.')

    fun toRunnerEvaluations(): List<RunnerEvaluation> {
        var i = 0
        return ReplayBenchmark
            .from(TriGSerializer.deserialize(FileDataSource(inputFilePath)).consume())
            .flatMap { benchmark ->
                val diffs = benchmark.store.diffs.toList()
                val nameBase = name
                benchmark.queries.map { query ->
                    val name = "$nameBase-${++i}"
                    RunnerEvaluation(
                        name = name,
                        inputFilePath = inputFilePath,
                        outputDirPath = outputDirPath.replace(nameBase, name),
                        evaluatorName = evaluatorName,
                        diffs = diffs,
                        query = query,
                        warmupRounds = warmups,
                        executionRounds = runs,
                    )
                }
            }
    }

    companion object {

        /**
         * Create all possible runner configuration variants based on the various parameters
         *
         * @param inputPaths The input filepath to use; can be a file or folder (in which case all valid files are used)
         * @param outputFolder The output filepath to use; has to be a folder!
         * @param evaluators All evaluator (names) to use
         * @param warmups The number of runs that contribute to the warmup
         * @param runs The number of runs to measure, executed after the warmups
         */
        fun createVariants(
            inputPaths: Collection<String>,
            outputFolder: String,
            evaluators: Collection<String>,
            warmups: Int,
            runs: Int,
        ): List<RunnerConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                evaluators.map { evaluator ->
                    RunnerConfig(
                        inputFilePath = input,
                        outputDirPath = "${outputFolder}$evaluator/$filename/",
                        evaluatorName = evaluator,
                        warmups = warmups,
                        runs = runs
                    )
                }
            }
        }

    }

}

expect val SELF_IMPL: String
