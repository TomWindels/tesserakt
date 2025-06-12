package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.impl.CachingSparqlEndpoint
import dev.tesserakt.sparql.endpoint.server.impl.SparqlEndpoint

/**
 * A simple [SparqlEndpoint] decorator, instantiating either a caching or regular endpoint based on the [EndpointConfig]
 */
class Endpoint(config: EndpointConfig): SparqlEndpoint {

    private val inner = if (!config.useCaching) {
        SparqlEndpoint(MutableStore())
    } else {
        CachingSparqlEndpoint(ObservableStore())
    }

    override suspend fun onSelectQueryRequest(query: String): Result<SelectResponse> {
        return inner.onSelectQueryRequest(query)
    }

    override suspend fun onUpdateQueryRequest(request: UpdateRequest): Result<Unit> {
        return inner.onUpdateQueryRequest(request)
    }

}
