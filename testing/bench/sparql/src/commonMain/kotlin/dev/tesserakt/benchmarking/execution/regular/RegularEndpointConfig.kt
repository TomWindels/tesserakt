package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.execution.EndpointUtil
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles

data class RegularEndpointConfig(
    val query: String,
    val inputFilePath: String?,
    val outputDirPath: String,
    val endpoint: String,
) {

    init {
        require(endpoint.startsWith("http://localhost:"))
    }

    fun toRunnerEvaluation(): RegularRunnerEvaluation {
        val name = inputFilePath
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?: "external-data"
        return RegularRunnerEvaluation(
            name = name,
            inputFilePath = inputFilePath,
            outputDirPath = outputDirPath,
            evaluatorName = EndpointUtil.endpointUrlToEvaluatorName(endpoint = endpoint),
            query = query,
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
         */
        fun createVariants(
            query: Iterable<String>,
            inputPaths: Iterable<String>,
            outputFolder: String,
            endpoints: Iterable<String>,
        ): List<RegularEndpointConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return if (inputs.isEmpty()) {
                query.flatMapIndexed { i, query ->
                    endpoints.map { endpoint ->
                        RegularEndpointConfig(
                            query = query,
                            inputFilePath = null,
                            outputDirPath = "${outputFolder}${EndpointUtil.endpointUrlToEvaluatorName(endpoint = endpoint)}/query_${i}/",
                            endpoint = endpoint,
                        )
                    }
                }
            } else inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                query.flatMapIndexed { i, query ->
                    endpoints.map { endpoint ->
                        RegularEndpointConfig(
                            query = query,
                            inputFilePath = input,
                            outputDirPath = "${outputFolder}${EndpointUtil.endpointUrlToEvaluatorName(endpoint = endpoint)}/$filename/query_${i}/",
                            endpoint = endpoint,
                        )
                    }
                }
            }
        }

    }

}
