package dev.tesserakt.sparql.endpoint.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*

class Server(config: EndpointConfig) {

    private val server = embeddedServer(Netty, port = config.port) {
        println("Initialising server with configuration $config")
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
        println("Starting server...")
        server.start(wait = true)
    }

}
