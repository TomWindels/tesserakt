package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import io.ktor.client.plugins.contentnegotiation.*

/**
 * Adds support for the `application/sparql-results+json` Content Type. Required when using [bodyAsBindings].
 */
fun ContentNegotiationConfig.sparql() {
    register(SparqlContentType.JsonBindings, SparqlBindingsContentConverter)
}
