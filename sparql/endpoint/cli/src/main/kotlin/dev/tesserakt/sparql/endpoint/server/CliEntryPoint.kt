package dev.tesserakt.sparql.endpoint.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class CliEntryPoint(private val run: (EndpointConfig) -> Unit) : CliktCommand(), EndpointConfig {

    override fun help(context: Context): String {
        return "Create a tesserakt-powered SPARQL endpoint from the command line"
    }

    override val path: String by option()
        .default("sparql")
        .help("The name of the endpoint")

    override val port: Int by option()
        .int()
        .default(3000)
        .help("The port number")

    private val disableCache by option()
        .flag(default = false, defaultForHelp = "cache enabled")
        .help("Disables the use of in-memory query caches")

    override val useCaching: Boolean get() = !disableCache

    override fun run() {
        run(this)
    }

}
