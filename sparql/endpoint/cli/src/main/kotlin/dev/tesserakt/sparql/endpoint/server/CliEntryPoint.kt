package dev.tesserakt.sparql.endpoint.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class CliEntryPoint(private val run: (EndpointConfig) -> Unit) : CliktCommand(), EndpointConfig {

    override val slug: String by option().default("sparql").help("The name of the endpoint")
    override val port: Int by option().int().default(3000).help("The port number")

    override fun run() {
        run(this)
    }

}
