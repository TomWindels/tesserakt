package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.trig.serialization.TriG
import dev.tesserakt.rdf.types.toStore
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

data class ReplayRunnerConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
) {

    override fun toString() =
        "Benchmark runner\n* Input: $inputFilePath\n* Output: $outputDirPath\n* Implementation: $evaluatorName"

    val name: String
        get() = inputFilePath.substringAfterLast('/').substringBeforeLast('.')

    fun toRunnerEvaluations(): List<ReplayRunnerEvaluation> {
        var i = 0
        return ReplayBenchmark
            .from(serializer(TriG).deserialize(FileDataSource(inputFilePath)).toStore())
            .flatMap { benchmark ->
                val nameBase = name
                benchmark.queries.map { query ->
                    val name = "$nameBase-${++i}"
                    ReplayRunnerEvaluation(
                        name = name,
                        inputFilePath = inputFilePath,
                        outputDirPath = outputDirPath.replace(nameBase, name),
                        evaluatorName = evaluatorName,
                        query = query,
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
         */
        fun createVariants(
            inputPaths: Collection<String>,
            outputFolder: String,
            evaluators: Collection<String>,
        ): List<ReplayRunnerConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return inputs.flatMapIndexed { i, input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                evaluators.map { evaluator ->
                    ReplayRunnerConfig(
                        inputFilePath = input,
                        outputDirPath = "${outputFolder}$evaluator/$filename/input_${i}/",
                        evaluatorName = evaluator,
                    )
                }
            }
        }

    }

}
