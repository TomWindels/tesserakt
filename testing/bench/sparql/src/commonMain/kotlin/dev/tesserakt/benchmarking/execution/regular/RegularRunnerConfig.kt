package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.isFile
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles

data class RegularRunnerConfig(
    val query: String,
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
) {

    override fun toString() =
        "Benchmark runner\n* Input: $inputFilePath\n* Output: $outputDirPath\n* Implementation: $evaluatorName"

    fun toRunnerEvaluation(): RegularRunnerEvaluation {
        val name = inputFilePath
            .substringAfterLast('/')
            .substringBeforeLast('.')
        return RegularRunnerEvaluation(
            name = name,
            inputFilePath = inputFilePath,
            outputDirPath = outputDirPath,
            evaluatorName = evaluatorName,
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
         * @param evaluators All evaluator (names) to use
         */
        fun createVariants(
            query: Iterable<String>,
            inputPaths: Iterable<String>,
            outputFolder: String,
            evaluators: Iterable<String>,
        ): List<RegularRunnerConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are actually files
                .filter { it.isFile() }
            return inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                query.flatMapIndexed { i, query ->
                    evaluators.map { evaluator ->
                        RegularRunnerConfig(
                            query = query,
                            inputFilePath = input,
                            outputDirPath = "${outputFolder}$evaluator/$filename/query_${i}/",
                            evaluatorName = evaluator,
                        )
                    }
                }
            }
        }

    }

}
