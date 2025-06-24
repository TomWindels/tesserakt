package dev.tesserakt.benchmarking.execution.regular

import dev.tesserakt.benchmarking.isFile
import dev.tesserakt.benchmarking.isFolder
import dev.tesserakt.benchmarking.listFiles
import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume

data class RegularRunnerConfig(
    val query: String,
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
    val warmups: Int,
    val runs: Int,
) {

    override fun toString() =
        "Benchmark runner\n* Input: $inputFilePath\n* Output: $outputDirPath\n* Implementation: $evaluatorName"

    fun toRunnerEvaluation(): RegularRunnerEvaluation {
        val name = inputFilePath
            .substringAfterLast('/')
            .substringBeforeLast('.')
        val data = TriGSerializer.deserialize(FileDataSource(inputFilePath)).consume()
        return RegularRunnerEvaluation(
            name = name,
            inputFilePath = inputFilePath,
            outputDirPath = outputDirPath,
            evaluatorName = evaluatorName,
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
         * @param evaluators All evaluator (names) to use
         * @param warmups The number of runs that contribute to the warmup
         * @param runs The number of runs to measure, executed after the warmups
         */
        fun createVariants(
            query: Iterable<String>,
            inputPaths: Iterable<String>,
            outputFolder: String,
            evaluators: Iterable<String>,
            warmups: Int,
            runs: Int,
        ): List<RegularRunnerConfig> {
            val inputs = inputPaths
                // flattening any and all folders (ONCE!)
                .flatMap { if (it.isFolder()) it.listFiles() else listOf(it) }
                // ensuring the remaining files are actually files
                .filter { it.isFile() }
            return inputs.flatMap { input ->
                val filename = input.substringAfterLast('/').substringBefore('.')
                query.flatMap { query ->
                    evaluators.map { evaluator ->
                        RegularRunnerConfig(
                            query = query,
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

}
