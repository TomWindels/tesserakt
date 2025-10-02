package dev.tesserakt.benchmarking.execution.replay

import dev.tesserakt.benchmarking.Endpoint
import dev.tesserakt.benchmarking.EvaluationConfig
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles

data class ReplayEvaluationConfig(
    val inputFilePath: String,
    override val warmup: EvaluationConfig.Warmup,
    override val outputDirPath: String,
    override val endpoint: Endpoint.Mutable,
) : EvaluationConfig {

    override val name: String = buildString {
        append(endpoint.queryUrl)
        append(", ")
        append(outputDirPath.substringAfterLast('/'))
        append(", ")
        append(inputFilePath.substringAfterLast('/'))
    }

    override fun metadata(): String = buildString {
        appendLine("endpoint: $endpoint")
        appendLine("warmup:")
        appendLine(warmup)
        appendLine("inputFilePath: $inputFilePath")
        append("outputDirPath: $outputDirPath")
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
            endpoints: Collection<Endpoint.Mutable>,
            warmup: EvaluationConfig.Warmup,
        ): List<ReplayEvaluationConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are turtle files
                .filter { it.endsWith(".ttl") }
            return inputs.flatMapIndexed { i, input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                endpoints.map { endpoint ->
                    ReplayEvaluationConfig(
                        inputFilePath = input,
                        outputDirPath = "${outputFolder}${endpoint}/$filename/input_${i}/",
                        endpoint = endpoint,
                        warmup = warmup
                    )
                }
            }
        }

    }

}
