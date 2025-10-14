package dev.tesserakt.sparql.endpoint.server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*

class Server(config: EndpointConfig) {

    private val server = embeddedServer(CIO, port = config.port) {
        println("Initialising server with configuration\n${config.toString().prependIndent(" | ")}")
        install(StatusPages) {
            exception<Throwable> { call: ApplicationCall, cause: Throwable ->
                log(call, cause)
            }
        }
        routing {
            if (config.verbose) {
                install(VerboseLogging)
            }
            sparqlEndpoint(
                path = config.path,
                endpoint = SparqlEndpoint(config)
            )
        }
    }

    fun run() {
        server.start(wait = true)
    }

}
