package dev.tesserakt.sparql.endpoint.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*

class Server(config: EndpointConfig) {

    private val server = embeddedServer(Netty, port = config.port) {
        sparqlEndpoint(config.slug)
    }

    fun run() {
        server.start(wait = true)
    }

}
