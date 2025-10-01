package dev.tesserakt.sparql.endpoint.server

import java.io.File

data class EndpointConfig(
    val port: Int,
    val path: String,
    val cacheSize: Int,
    val verbose: Boolean,
    val start: File?,
) {

    override fun toString() = buildString {
        if (verbose) {
            appendLine("tesserakt - SPARQL endpoint configuration (verbose)")
        } else {
            appendLine("tesserakt - SPARQL endpoint configuration")
        }
        append("port = ")
        appendLine(port)
        append("path = ")
        appendLine(path)
        append("cacheSize = ")
        append(if (cacheSize == 0) "disabled" else cacheSize)
        if (start != null) {
            append('\n')
            append("filepath = ")
            append(start.absolutePath)
        }
    }

}
