package dev.tesserakt.benchmarking

data class RunnerConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val evaluatorName: String,
) {

    private val factory = when (evaluatorName) {
        SELF_IMPL -> { query: String -> Self(query) }
        in references -> { query: String -> references[evaluatorName]!!.invoke(query) }
        else -> throw IllegalArgumentException("Unknown evaluator: `${evaluatorName}`\nValid evaluators: ${implementations.joinToString { "\"$it\"" }}")
    }

    fun createRunner() = Runner(config = this)

    fun createEvaluator(query: String) = factory(query)

    override fun toString() =
        "Benchmark runner\n* Input: $inputFilePath\n* Output: $outputDirPath\n* Implementation: $evaluatorName"

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
            var references: List<String>? = null
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
                        if (i >= args.size) {
                            throw IllegalArgumentException("No output filepath provided!")
                        } else {
                            output = args[i]
                        }
                    }

                    args[i].isSpecificImplementationFlag() -> {
                        ++i
                        if (i >= args.size) {
                            throw IllegalArgumentException("Reference name is missing!")
                        }
                        references = args[i].split(',')
                    }

                    args[i].isComparisonFlag() -> {
                        createComparison = true
                    }

                    args[i].isListImplementationsFlag() -> {
                        println("Available implementations: ${implementations.joinToString { "\"$it\"" }}")
                        // short-circuiting, not doing anything else with the arguments
                        return emptyList()
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
                    input = input.dropLastWhile { it == '/' }
                    val files = input.listFiles().filter { it.endsWith(".ttl") }
                    if (createComparison) {
                        implementations.flatMap { implementation ->
                            files.map { name ->
                                val filename = name.substringAfterLast('/').substringBefore('.')
                                RunnerConfig(
                                    inputFilePath = name,
                                    outputDirPath = if (output != null) "${output}$filename-$implementation/" else name.createOutputFilepath(
                                        implementation
                                    ),
                                    evaluatorName = implementation
                                )
                            }
                        }
                    } else if (references != null) {
                        references.flatMap { implementation ->
                            files.map { name ->
                                val filename = name.substringAfterLast('/').substringBefore('.')
                                RunnerConfig(
                                    inputFilePath = name,
                                    outputDirPath = if (output != null) "${output}$filename-$implementation/" else name.createOutputFilepath(
                                        implementation
                                    ),
                                    evaluatorName = implementation
                                )
                            }
                        }
                    } else {
                        files.map { name ->
                            val filename = name.substringAfterLast('/').substringBefore('.')
                            RunnerConfig(
                                inputFilePath = name,
                                outputDirPath = if (output != null) "${output}$filename-self/" else name.createOutputFilepath(
                                    SELF_IMPL
                                ),
                                evaluatorName = SELF_IMPL
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
                        implementations.map { implementation ->
                            RunnerConfig(
                                inputFilePath = input,
                                outputDirPath = if (output != null) "${output}$filename-$implementation/" else input.createOutputFilepath(
                                    implementation
                                ),
                                evaluatorName = implementation
                            )
                        }
                    } else if (references != null) {
                        references.map { implementation ->
                            RunnerConfig(
                                inputFilePath = input,
                                outputDirPath = if (output != null) "${output}$filename-$implementation" else input.createOutputFilepath(
                                    implementation
                                ),
                                evaluatorName = implementation
                            )
                        }
                    } else {
                        listOf(
                            RunnerConfig(
                                inputFilePath = input,
                                outputDirPath = if (output != null) "${output}$filename-$SELF_IMPL" else input.createOutputFilepath(
                                    SELF_IMPL
                                ),
                                evaluatorName = SELF_IMPL
                            )
                        )
                    }
                }
            }
        }

        private fun String.isInputFlag() = this == "-i" || this == "--input"

        private fun String.isOutputFlag() = this == "-o" || this == "--output"

        private fun String.isSpecificImplementationFlag() = this == "-u" || this == "--use"

        private fun String.isComparisonFlag() = this == "--compare-implementations"

        private fun String.isListImplementationsFlag() = this == "-l" || this == "--list-implementations"

        private fun String.createOutputFilepath(implementation: String) =
            this.dropLast(4) + "_${implementation}_${currentEpochMs()}/"

        private val implementations = listOf(SELF_IMPL) + references.keys

    }

}

expect val SELF_IMPL: String
