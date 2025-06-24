package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.execution.EndpointUtil
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume

data class RegularEndpointConfig(
    val query: String,
    val inputFilePath: String?,
    val outputDirPath: String,
    val endpoint: String,
    val warmups: Int,
    val runs: Int,
) {

    init {
        require(endpoint.startsWith("http://localhost:"))
    }

    fun toRunnerEvaluation(): RegularRunnerEvaluation {
        val name = inputFilePath
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?: "external-data"
        val data = inputFilePath?.let { TriGSerializer.deserialize(FileDataSource(it)).consume() }
        return RegularRunnerEvaluation(
            name = name,
            inputFilePath = inputFilePath,
            outputDirPath = outputDirPath,
            evaluatorName = EndpointUtil.endpointUrlToEvaluatorName(endpoint = endpoint),
            store = data,
            query = query,
            warmupRounds = warmups,
            executionRounds = runs,
        )
    }

    companion object {

        /**
         * Create all possible runner configuration variants based on the various parameters
         *
         * @param query The query string(s) to use
         * @param inputPaths The input filepath to use; can be a file or folder (in which case all valid files are used)
         * @param outputFolder The output filepath to use; has to be a folder!
         * @param endpoints All evaluator endpoints (URLs) to use
         * @param warmups The number of runs that contribute to the warmup
         * @param runs The number of runs to measure, executed after the warmups
         *
         */
        fun createVariants(
            query: Iterable<String>,
            inputPaths: Iterable<String>,
            outputFolder: String,
            endpoints: Iterable<String>,
            warmups: Int,
            runs: Int,
        ): List<RegularEndpointConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return if (inputs.isEmpty()) {
                query.flatMap { query ->
                    endpoints.map { endpoint ->
                        RegularEndpointConfig(
                            query = query,
                            inputFilePath = null,
                            outputDirPath = "${outputFolder}${EndpointUtil.endpointUrlToEvaluatorName(endpoint = endpoint)}/",
                            endpoint = endpoint,
                            warmups = warmups,
                            runs = runs
                        )
                    }
                }
            } else inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                query.flatMap { query ->
                    endpoints.map { endpoint ->
                        RegularEndpointConfig(
                            query = query,
                            inputFilePath = input,
                            outputDirPath = "${outputFolder}${EndpointUtil.endpointUrlToEvaluatorName(endpoint = endpoint)}/$filename/",
                            endpoint = endpoint,
                            warmups = warmups,
                            runs = runs
                        )
                    }
                }
            }
        }

    }

}
