package dev.tesserakt.sparql.endpoint.server

import io.ktor.server.application.*
import io.ktor.server.request.*

internal fun log(call: ApplicationCall) {
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
}

internal fun log(call: ApplicationCall, exception: Throwable) {
    log(call)
    // adding a newline between both statements
    println()
    exception.printStackTrace()
}

val VerboseLogging = createApplicationPlugin("VerboseLogging") {
    onCall {
        log(it)
    }
}
