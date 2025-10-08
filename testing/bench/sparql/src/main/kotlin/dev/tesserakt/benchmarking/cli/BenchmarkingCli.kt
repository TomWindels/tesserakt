package dev.tesserakt.benchmarking.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import dev.tesserakt.benchmarking.*
import dev.tesserakt.benchmarking.execution.Benchmark
import dev.tesserakt.benchmarking.execution.regular.RegularEvaluationConfig
import dev.tesserakt.benchmarking.execution.replay.ReplayEvaluationConfig
import dev.tesserakt.benchmarking.execution.update.UpdateEvaluationConfig

class BenchmarkingCli: SuspendingCliktCommand("sparql-bench") {

    // subcommand aliases
    override fun aliases(): Map<String, List<String>> = mapOf(
        "q" to listOf("query"),
        "r" to listOf("replay"),
        "u" to listOf("update"),
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

        val endpoints: Set<Endpoint> by option(
                "--url",
                help = "Provide a SPARQL endpoint URL to use (multiple supported)",
            )
            .convert { base ->
                // assuming the URL(s) don't contain comma's
                val params = base.split(',')
                require(params.size in 1..3) { "The provided endpoint URL parameter `${base}` is ill-formed! Expected format `http://x.y.z/query[,http://x.y.z/update[,update-token]]`" }
                if (!params.first().matches(URL) || params.size >= 2 && !params[1].matches(URL)) {
                    fail("The start argument(s) should be a valid URL, structure `${base}` is invalid.")
                }
                if (params.size == 3 && params.last().matches(URL)) {
                    // assuming wrong input, as a URL-like access token seems unlikely
                    fail("Unexpected URL-like parameter, access token required")
                }
                when (params.size) {
                    1 -> Endpoint.Immutable(
                        queryUrl = params[0],
                    )
                    2 -> Endpoint.Mutable(
                        queryUrl = params[0],
                        updateUrl = params[1],
                        token = null,
                    )
                    3 -> Endpoint.Mutable(
                        queryUrl = params[0],
                        updateUrl = params[1],
                        token = params[2],
                    )
                    else -> throw RuntimeException()
                }
            }
            .multiple(required = true)
            .unique()

        private val warmupQueries: Set<String> by option(
                "--warmup-query",
                help = "The warmup query to evaluate. This query is executed before the first evaluation of the to-be-evaluated query during the warmup phase. Multiple supported",
            )
            .convert { if (it.startsWith('@')) it.substring(1).readFile() else it }
            .multiple(required = false)
            .unique()

        private val warmupRuns: Int by option(
                "--warmup-runs",
                help = "The number of executions of all warmup queries before evaluation",
            )
            .int()
            .default(1)

        fun getWarmup(): EvaluationConfig.Warmup = EvaluationConfig.Warmup(queries = warmupQueries.toList(), runs = warmupRuns)

    }

    class Query: SuspendingCliktCommand("query") {

        private val common by CommonSettings()

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
            val endpointConfigs = RegularEvaluationConfig.createVariants(
                query = query,
                inputPaths = input,
                outputFolder = common.output,
                endpoints = common.endpoints,
                warmup = common.getWarmup(),
            )
            val host = Benchmark(
                evaluations = endpointConfigs,
            )
            println("Executing ${host.evaluations.size} evaluation(s)")
            host.run()
        }

    }

    class Replay: SuspendingCliktCommand("replay") {

        private val common by CommonSettings()

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
            val endpointConfigs = ReplayEvaluationConfig.createVariants(
                inputPaths = input,
                outputFolder = common.output,
                endpoints = common.endpoints.filterIsInstance<Endpoint.Mutable>(),
                warmup = common.getWarmup(),
            )
            // then mapping these to the various evaluations we can actually evaluate
            val host = Benchmark(
                evaluations = endpointConfigs,
            )
            println("Executing ${host.evaluations.size} evaluation(s)")
            host.run()
        }

    }

    class Update: SuspendingCliktCommand("update") {

        private val common by CommonSettings()

        private val update: String by option(
                "--update-file", "-u",
                help = "Path to the file containing the update",
                completionCandidates = CompletionCandidates.Path,
            )
            .required()

        private val query: String by option(
            "--query", "-q",
            help = "Query to evaluate",
        )
            .convert { if (it.startsWith('@')) it.substring(1).readFile() else it }
            .required()

        override fun help(context: Context): String {
            return "Benchmark the performance of a query over a dataset that is altered with a specific update between executions"
        }

        override suspend fun run() {
            // creating all configs up-front
            val endpointConfigs = common
                .endpoints
                .filterIsInstance<Endpoint.Mutable>()
                .map { endpoint ->
                    UpdateEvaluationConfig(
                        query = query,
                        updateFilePath = update,
                        outputDirPath = common.output,
                        endpoint = endpoint,
                        warmup = common.getWarmup(),
                    )
                }
            // then mapping these to the various evaluations we can actually evaluate
            val host = Benchmark(
                evaluations = endpointConfigs,
            )
            println("Executing ${host.evaluations.size} evaluation(s)")
            host.run()
        }

    }

}

private val URL = Regex("https?://.*")

suspend fun runMain(args: Array<String>) = BenchmarkingCli()
    .subcommands(
        BenchmarkingCli.Query(),
        BenchmarkingCli.Replay(),
        BenchmarkingCli.Update(),
    )
    .main(args)
