package dev.tesserakt.sparql.endpoint.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int

class CliEntryPoint(private val run: (EndpointConfig) -> Unit) : CliktCommand() {

    override fun help(context: Context): String {
        return "Create a tesserakt-powered SPARQL endpoint from the command line"
    }

    private val path: String by option()
        .default("sparql")
        .help("The name of the endpoint")

    private val port: Int by option()
        .int()
        .default(3000)
        .help("The port number")

    private val disableCache by option()
        .flag(default = false, defaultForHelp = "cache enabled")
        .help("Disables the use of in-memory query caches")

    private val verbose by option()
        .flag(default = false)
        .help("Enable additional logging")

    private val start by option("--from-file")
        .file(mustExist = true, mustBeReadable = true, canBeFile = true, canBeSymlink = true)
        .help("Use a dataset as an initial value for the in-memory store (can be N-Triples, Turtle or TriG)")

    override fun run() {
        run(toConfig())
    }

    private fun toConfig(): EndpointConfig = EndpointConfig(
        port = port,
        path = path,
        useCaching = !disableCache,
        verbose = verbose,
        start = start,
    )

}
