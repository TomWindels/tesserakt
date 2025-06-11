package dev.tesserakt.sparql.endpoint.server

interface EndpointConfig {
    val port: Int
    val path: String
    val useCaching: Boolean
}
