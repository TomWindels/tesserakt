package dev.tesserakt.benchmarking

import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark

data class EndpointConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val endpoint: String,
    val warmups: Int,
    val runs: Int,
) {

    init {
        require(endpoint.startsWith("http://localhost:"))
    }

    fun toRunnerEvaluations(): List<RunnerEvaluation> {
        var i = 0
        return ReplayBenchmark
            .from(TriGSerializer.deserialize(FileDataSource(inputFilePath)).consume())
            .flatMap { benchmark ->
                val diffs = benchmark.store.diffs.toList()
                val nameBase = inputFilePath.substringAfterLast('/').substringBeforeLast('.')
                benchmark.queries.map { query ->
                    val name = "$nameBase-${++i}"
                    RunnerEvaluation(
                        name = name,
                        inputFilePath = inputFilePath,
                        outputDirPath = outputDirPath.replace(nameBase, name),
                        evaluatorName = endpointUrlToEvaluatorName(endpoint = endpoint),
                        diffs = diffs,
                        query = query,
                        warmupRounds = warmups,
                        executionRounds = runs
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
         * @param warmups The number of runs that contribute to the warmup
         * @param runs The number of runs to measure, executed after the warmups
         *
         */
        fun createVariants(
            inputPaths: Collection<String>,
            outputFolder: String,
            endpoints: Collection<String>,
            warmups: Int,
            runs: Int,
        ): List<EndpointConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                endpoints.map { endpoint ->
                    EndpointConfig(
                        inputFilePath = input,
                        outputDirPath = "${outputFolder}${endpointUrlToEvaluatorName(endpoint = endpoint)}/$filename/",
                        endpoint = endpoint,
                        warmups = warmups,
                        runs = runs
                    )
                }
            }
        }

        fun endpointUrlToEvaluatorName(endpoint: String): String {
            return "endpoint_${endpoint.removePrefix("http://localhost:").replace("/", "%2F")}"
        }

        fun evaluatorNameToEndpointUrl(name: String): String {
            return "http://localhost:${name.removePrefix("endpoint_").replace("%2F", "/")}"
        }

    }

}
