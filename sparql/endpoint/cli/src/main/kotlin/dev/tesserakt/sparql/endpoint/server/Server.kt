package dev.tesserakt.sparql.endpoint.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*

class Server(config: EndpointConfig) {

    private val server = embeddedServer(Netty, port = config.port) {
        install(StatusPages) {
            exception<Throwable> { call: ApplicationCall, cause: Throwable ->
                log(call, cause)
            }
        }
        sparqlEndpoint(config.slug)
    }

    fun run() {
        server.start(wait = true)
    }

    private fun log(call: ApplicationCall, exception: Throwable) {
        val request = call.request
        println("*")
        println(">  ${request.httpMethod.value} ${request.path()}")
        val requestHeaders = request.headers.entries().joinToString("\n") { ">  ${it.key}: ${it.value.joinToString()}" }
        if (requestHeaders.isNotBlank()) {
            println(requestHeaders)
        }

        val response = call.response
        println("<  ${response.status() ?: "no status code"}")
        val responseHeaders = response.headers.allValues().entries().joinToString("\n") { "<  ${it.key}: ${it.value.joinToString()}" }
        if (responseHeaders.isNotBlank()) {
            println(responseHeaders)
        }
        println()

        exception.printStackTrace()
    }

}
