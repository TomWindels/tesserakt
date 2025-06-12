package dev.tesserakt.benchmarking

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice

class BenchmarkingCli: SuspendingCliktCommand("sparql-bench") {

    private val input: Set<String> by option(
            "--input", "-i",
            help = "Select the input filepath to use (can be a replay format)",
            completionCandidates = CompletionCandidates.Path,
        )
        .multiple(required = true)
        .unique()

    private val output: String by option(
            "--output", "-o",
            help = "The output filepath to use",
            completionCandidates = CompletionCandidates.Path,
        )
        .required()
        .check(
            lazyMessage = { "Output filepath `$it` is not a folder, or is not empty!" },
            validator = { it.tryMakeFolder() && it.listFiles().isEmpty() }
        )

    private val implementations: Set<String> by option(
            "--use-engine", "-e",
            help = "Select an engine implementation to use (multiple supported)",
        )
        .choice(
            *(references.keys + "tesserakt" + "all").toTypedArray(),
            ignoreCase = true
        )
        .multiple(default = emptyList())
        .unique()

    private val endpoints: Set<String> by option(
            "--url", "-u",
            help = "Provide a SPARQL endpoint URL to use (multiple supported)",
        )
        .multiple()
        .unique()
        .check(lazyMessage = { "Invalid URL encountered!" }) { it.all { entry -> entry.matches(URL) } }

    override suspend fun run() {
        val mapped = if ("all" in implementations) references.keys + SELF_IMPL else implementations.map { if (it == "tesserakt") SELF_IMPL else it }
        require(mapped.isNotEmpty() || endpoints.isNotEmpty()) { "No executions requested - see `-h` for options" }
        // creating all configs up-front
        val localConfigs = RunnerConfig.createVariants(
            inputPaths = input,
            outputFolder = output,
            evaluators = mapped,
        )
        val endpointConfigs = EndpointConfig.createVariants(
            inputPaths = input,
            outputFolder = output,
            endpoints = endpoints
        )
        // then mapping these to the various evaluations we can actually evaluate
        localConfigs.forEach { config ->
            val evaluations = config.toRunnerEvaluations()
            evaluations.forEach {
                val runner = it.createRunner()
                runner.run()
            }
        }
        endpointConfigs.forEach { config ->
            val evaluations = config.toRunnerEvaluations()
            evaluations.forEach {
                val runner = it.createRunner()
                runner.run()
            }
        }
    }

}

private val URL = Regex("https?://.*")
