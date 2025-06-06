package dev.tesserakt.sparql.endpoint.client

import dev.tesserakt.sparql.endpoint.core.SparqlContentType
import io.ktor.client.plugins.contentnegotiation.*


fun ContentNegotiationConfig.sparql() {
    register(SparqlContentType.JsonBindings, SparqlBindingsContentConverter)
}
