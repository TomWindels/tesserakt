package dev.tesserakt.benchmarking

data class RunnerConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val referenceImplementation: Boolean
) {

    fun createRunner() = Runner(config = this)

    override fun toString() =
        "Benchmark runner\n* Input: $inputFilePath\n* Output: $outputDirPath\n* Reference: $referenceImplementation"

    companion object {

        /**
         * Creating a Runner instantiated from the command line, formatted as
         * ```
         * runner [-i/--input] path/to/benchmark.ttl [-o/--output] [path/to/output_dir] [--use-reference-implementation]
         * ```
         * or
         * ```
         * runner -o/--output path/to/output_dir path/to/benchmark.ttl
         * ```
         * etc.
         */
        fun fromCommandLine(args: Array<String>): List<RunnerConfig> {
            var input: String? = null
            var output: String? = null
            var i = 0
            var useReference = false
            var createComparison = false
            while (i < args.size) {
                when {
                    args[i].isInputFlag() -> {
                        ++i
                        if (i >= args.size) {
                            throw IllegalArgumentException("No input filepath provided!")
                        } else {
                            input = args[i]
                        }
                    }
                    args[i].isOutputFlag() -> {
                        ++i
                        if (i>= args.size) {
                            throw IllegalArgumentException("No output filepath provided!")
                        } else {
                            output = args[i]
                        }
                    }
                    args[i].isReferenceImplementationFlag() -> {
                        useReference = true
                    }
                    args[i].isComparisonFlag() -> {
                        createComparison = true
                    }
                    input == null -> {
                        input = args[i]
                    }
                    output == null -> {
                        output = args[i]
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid command line structure!")
                    }
                }
                ++i
            }
            if (input == null) {
                throw IllegalArgumentException("No input filepath provided!")
            }
            if (output != null && output.last() != '/') {
                throw IllegalArgumentException("Invalid output path! Directory expected!")
            }
            return when {
                input.isFolder() -> {
                    val files = input.listFiles().filter { it.endsWith(".ttl") }
                    if (createComparison) {
                        files.map { name ->
                            val filename = name.substringAfterLast('/').substringBefore('.')
                            RunnerConfig(
                                inputFilePath = name,
                                outputDirPath = if (output != null) "${output}$filename-self/" else name.createOutputFilepath(),
                                referenceImplementation = false
                            )
                        } + files.map { name ->
                            val filename = name.substringAfterLast('/').substringBefore('.')
                            RunnerConfig(
                                inputFilePath = name,
                                outputDirPath = if (output != null) "${output}$filename-reference/" else name.createOutputFilepath(),
                                referenceImplementation = true
                            )
                        }
                    } else {
                        files.map { name ->
                            val filename = name.substringAfterLast('/').substringBefore('.')
                            RunnerConfig(
                                inputFilePath = name,
                                outputDirPath = if (output != null) "${output}$filename-${if (useReference) "-reference" else "-self"}/" else name.createOutputFilepath(),
                                referenceImplementation = useReference
                            )
                        }
                    }
                }

                else -> {
                    if (!input.endsWith(".ttl")) {
                        throw IllegalArgumentException("Invalid input filepath! `.ttl` file expected!")
                    }
                    val filename = input.substringAfterLast('/').substringBefore('.')
                    if (createComparison) {
                        listOf(
                            RunnerConfig(
                                inputFilePath = input,
                                outputDirPath = if (output != null) "${output}$filename-self/" else input.createOutputFilepath(),
                                referenceImplementation = false
                            ),
                            RunnerConfig(
                                inputFilePath = input,
                                outputDirPath = if (output != null) "${output}$filename-reference/" else input.createOutputFilepath(),
                                referenceImplementation = true
                            ),
                        )
                    } else {
                        listOf(
                            RunnerConfig(
                                inputFilePath = input,
                                outputDirPath = if (output != null) "${output}$filename-${if (useReference) "-reference" else "-self"}/" else input.createOutputFilepath(),
                                referenceImplementation = useReference
                            )
                        )
                    }
                }
            }
        }

        private fun String.isInputFlag() = this == "-i" || this == "--input"

        private fun String.isOutputFlag() = this == "-o" || this == "--output"

        private fun String.isReferenceImplementationFlag() = this == "--use-reference-implementation"

        private fun String.isComparisonFlag() = this == "--compare-implementations"

        private fun String.createOutputFilepath() = this.dropLast(3) + "_${currentEpochMs()}/"

    }

}
