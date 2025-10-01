package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.Endpoint
import dev.tesserakt.benchmarking.EvaluationConfig
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles

data class RegularEvaluationConfig(
    val query: String,
    val inputFilePath: String?,
    override val outputDirPath: String,
    override val endpoint: Endpoint,
) : EvaluationConfig {

    override val name: String
        get() = buildString {
            append(endpoint.queryUrl)
            append(", ")
            append(outputDirPath.substringAfterLast('/'))
            if (inputFilePath != null) {
                append(", ")
                append(inputFilePath.substringAfterLast('/'))
            }
        }

    override fun metadata(): String = buildString {
        appendLine("endpoint: $endpoint")
        appendLine("inputFilePath: $inputFilePath")
        appendLine("outputDirPath: $outputDirPath")
        append("query: $query")
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
            endpoints: Iterable<Endpoint>,
        ): List<RegularEvaluationConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return if (inputs.isEmpty()) {
                query.flatMapIndexed { i, query ->
                    endpoints.map { endpoint ->
                        RegularEvaluationConfig(
                            query = query,
                            inputFilePath = null,
                            outputDirPath = "${outputFolder}${endpoint}/query_${i}/",
                            endpoint = endpoint,
                        )
                    }
                }
            } else inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                query.flatMapIndexed { i, query ->
                    endpoints.map { endpoint ->
                        RegularEvaluationConfig(
                            query = query,
                            inputFilePath = input,
                            outputDirPath = "${outputFolder}${endpoint}/$filename/query_${i}/",
                            endpoint = endpoint,
                        )
                    }
                }
            }
        }

    }

}
