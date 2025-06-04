package dev.tesserakt.sparql.endpoint

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*

class Server(config: EndpointConfig) {

    private val server = embeddedServer(Netty, port = config.port) {
        install(ContentNegotiation) {
            json()
        }

        sparqlEndpoint(config.slug)
    }

    fun run() {
        server.start(wait = true)
    }

}
