package dev.tesserakt.benchmarking

data class RunnerConfig(
    val inputFilePath: String,
    val outputDirPath: String,
    val referenceImplementation: Boolean
) {

    fun createRunner() = Runner(
        inputFilePath = inputFilePath,
        outputDirPath = outputDirPath,
        referenceImplementation = referenceImplementation
    )

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
                    input.listFiles().filter { it.endsWith(".ttl") }.map { i ->
                        val filename = i.substringAfterLast('/').substringBefore('.')
                        RunnerConfig(
                            inputFilePath = i,
                            outputDirPath = if (output != null) "${output}$filename/" else i.createOutputFilepath(),
                            referenceImplementation = useReference
                        )
                    }
                }

                else -> {
                    if (!input.endsWith(".ttl")) {
                        throw IllegalArgumentException("Invalid input filepath! `.ttl` file expected!")
                    }
                    listOf(
                        RunnerConfig(
                            inputFilePath = input,
                            outputDirPath = output ?: input.createOutputFilepath(),
                            referenceImplementation = useReference
                        )
                    )
                }
            }
        }

        private fun String.isInputFlag() = this == "-i" || this == "--input"

        private fun String.isOutputFlag() = this == "-o" || this == "--output"

        private fun String.isReferenceImplementationFlag() = this == "--use-reference-implementation"

        private fun String.createOutputFilepath() = this.dropLast(3) + "_${currentEpochMs()}/"

    }

}
