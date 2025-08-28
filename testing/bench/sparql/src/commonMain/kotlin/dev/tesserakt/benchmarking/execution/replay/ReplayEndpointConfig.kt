package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.EvaluatorId
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

data class ReplayEndpointConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val endpoint: EvaluatorId.Endpoint.Mutable,
) {

    fun toRunnerEvaluations(): List<ReplayRunnerEvaluation> {
        var i = 0
        return ReplayBenchmark
            .from(TriGSerializer.deserialize(FileDataSource(inputFilePath)).consume())
            .flatMap { benchmark ->
                val nameBase = inputFilePath.substringAfterLast('/').substringBeforeLast('.')
                benchmark.queries.map { query ->
                    val name = "$nameBase-${++i}"
                    ReplayRunnerEvaluation(
                        name = name,
                        inputFilePath = inputFilePath,
                        outputDirPath = outputDirPath.replace(nameBase, name),
                        evaluatorId = endpoint,
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
         * @param endpoints All evaluator endpoints (URLs) to use
         */
        fun createVariants(
            inputPaths: Collection<String>,
            outputFolder: String,
            endpoints: Collection<EvaluatorId.Endpoint.Mutable>,
        ): List<ReplayEndpointConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return inputs.flatMapIndexed { i, input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                endpoints.map { endpoint ->
                    ReplayEndpointConfig(
                        inputFilePath = input,
                        outputDirPath = "${outputFolder}${endpoint}/$filename/input_${i}/",
                        endpoint = endpoint,
                    )
                }
            }
        }

    }

}
