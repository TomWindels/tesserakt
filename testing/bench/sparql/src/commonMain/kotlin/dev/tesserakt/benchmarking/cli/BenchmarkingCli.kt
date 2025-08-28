package dev.tesserakt.benchmarking.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import dev.tesserakt.benchmarking.*
import dev.tesserakt.benchmarking.execution.BenchmarkRunnerHost
import dev.tesserakt.benchmarking.execution.regular.RegularEndpointConfig
import dev.tesserakt.benchmarking.execution.regular.RegularRunnerConfig
import dev.tesserakt.benchmarking.execution.replay.ReplayEndpointConfig
import dev.tesserakt.benchmarking.execution.replay.ReplayRunnerConfig

class BenchmarkingCli: SuspendingCliktCommand("sparql-bench") {

    // subcommand aliases
    override fun aliases(): Map<String, List<String>> = mapOf(
        "q" to listOf("query"),
        "r" to listOf("replay"),
    )

    // can't use the no-op variant as that doesn't support the use of `suspend`ing variants down
    //  below
    override suspend fun run() {
        /* nothing to do */
    }

    class CommonSettings: OptionGroup() {

        val output: String by option(
                "--output", "-o",
                help = "The output filepath to use",
                completionCandidates = CompletionCandidates.Path,
            )
            .convert { if (!it.endsWith('/')) "$it/" else it }
            .required()
            .check(
                lazyMessage = { "Output filepath `$it` is not a folder, or is not empty!" },
                validator = { it.tryMakeFolder() && it.listFiles().isEmpty() }
            )

        val implementations: Set<EvaluatorId.Named> by option(
                "--use-engine", "-e",
                help = "Select an engine implementation to use (multiple supported)",
            )
            .choice(
                *(references.keys.map { it.name } + "tesserakt" + "all").toTypedArray(),
                ignoreCase = true
            )
            .convert { EvaluatorId.Named(it) }
            .multiple(default = emptyList())
            .unique()

        val endpoints: Set<EvaluatorId.Endpoint> by option(
                "--url", "-u",
                help = "Provide a SPARQL endpoint URL to use (multiple supported)",
            )
            .convert {
                // assuming the URL(s) don't contain comma's
                val urls = it.split(',')
                require(urls.size in 1..2) { "The provided endpoint URL parameter `${it}` is ill-formed!" }
                val invalid = urls.firstOrNull { url -> !url.matches(URL) }
                require(invalid == null) { "Entry contains an invalid URL: `$invalid`" }
                when (urls.size) {
                    1 -> EvaluatorId.Endpoint.Immutable(
                        queryUrl = urls[0],
                    )
                    2 -> EvaluatorId.Endpoint.Mutable(
                        queryUrl = urls[0],
                        updateUrl = urls[1]
                    )
                    else -> throw RuntimeException()
                }
            }
            .multiple()
            .unique()

        val runs: Int by option(
                "--runs",
                help = "The number of runs for every benchmark",
            )
            .int()
            .default(10)

        fun validate() {
            if (implementations.isEmpty() && endpoints.isEmpty()) {
                throw CliktError("No implementations or endpoints configured - see `-h` for options")
            }
        }

    }

    class Query: SuspendingCliktCommand("query") {

        private val common by CommonSettings()
        private val platformOptions by PlatformOptions()

        private val input: Set<String> by option(
                "--input", "-i",
                help = "Select the input filepath(s) to use (has to be a valid Turtle/TriG file); not providing any results in a single data test per evaluation, without manipulating the data itself",
                completionCandidates = CompletionCandidates.Path,
            )
            .multiple(required = false)
            .unique()

        private val query: Set<String> by option(
                "--query", "-q",
                help = "Query to evaluate",
            )
            .convert { if (it.startsWith('@')) it.substring(1).readFile() else it }
            .multiple(required = true)
            .unique()

        override fun help(context: Context): String {
            return "Benchmark the performance when evaluating a specific query over a fixed dataset"
        }

        override suspend fun run() {
            common.validate()
            platformOptions.apply()
            val mapped  = if (EvaluatorId.Named("all") in common.implementations) {
                references.keys + SELF_IMPL
            } else {
                common.implementations.map { if (it.name == "tesserakt") SELF_IMPL else it }
            }
            // creating all configs up-front
            val localConfigs = RegularRunnerConfig.createVariants(
                query = query,
                inputPaths = input,
                outputFolder = common.output,
                evaluators = mapped,
            )
            val endpointConfigs = RegularEndpointConfig.createVariants(
                query = query,
                inputPaths = input,
                outputFolder = common.output,
                endpoints = common.endpoints,
            )
            val host = BenchmarkRunnerHost(
                executions = common.runs,
                evaluations = localConfigs.map { it.toRunnerEvaluation() } + endpointConfigs.map { it.toRunnerEvaluation() },
            )
            println("Executing ${host.evaluations.size} evaluation(s) [x${host.executions}]")
            host.run()
        }

    }

    class Replay: SuspendingCliktCommand("replay") {

        private val common by CommonSettings()
        private val platformOptions by PlatformOptions()

        private val input: Set<String> by option(
                "--input", "-i",
                help = "Select the input filepath to use (has to be a replay format)",
                completionCandidates = CompletionCandidates.Path,
            )
            .multiple(required = true)
            .unique()

        override fun help(context: Context): String {
            return "Benchmark the performance when evaluating a specific query over a changing dataset"
        }

        override suspend fun run() {
            common.validate()
            platformOptions.apply()
            val mapped  = if (EvaluatorId.Named("all") in common.implementations) {
                references.keys + SELF_IMPL
            } else {
                common.implementations.map { if (it.name == "tesserakt") SELF_IMPL else it }
            }
            // creating all configs up-front
            val localConfigs = ReplayRunnerConfig.createVariants(
                inputPaths = input,
                outputFolder = common.output,
                evaluators = mapped,
            )
            val endpointConfigs = ReplayEndpointConfig.createVariants(
                inputPaths = input,
                outputFolder = common.output,
                endpoints = common.endpoints.filterIsInstance<EvaluatorId.Endpoint.Mutable>(),
            )
            // then mapping these to the various evaluations we can actually evaluate
            val host = BenchmarkRunnerHost(
                executions = common.runs,
                evaluations = localConfigs.flatMap { it.toRunnerEvaluations() } + endpointConfigs.flatMap { it.toRunnerEvaluations() },
            )
            println("Executing ${host.evaluations.size} evaluation(s) [x${host.executions}]")
            host.run()
        }

    }

}

private val URL = Regex("https?://.*")

suspend fun runMain(args: Array<String>) = BenchmarkingCli()
    .subcommands(
        BenchmarkingCli.Query(),
        BenchmarkingCli.Replay(),
    )
    .main(args)
