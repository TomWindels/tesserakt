package dev.tesserakt.sparql.endpoint.server

fun SparqlEndpoint(config: EndpointConfig): SparqlEndpoint {
    return if (config.verbose) VerboseEndpoint(config) else Endpoint(config)
}
