package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.rdf.types.factory.emptyStore
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.impl.CachingSparqlEndpoint
import dev.tesserakt.sparql.endpoint.server.impl.SparqlEndpoint

/**
 * A simple [SparqlEndpoint] decorator, instantiating either a caching or regular endpoint based on the [EndpointConfig]
 */
class Endpoint(config: EndpointConfig): SparqlEndpoint {

    private val inner = run {
        val base = if (config.start != null) {
            TriGSerializer.deserialize(FileDataSource(config.start)).consume()
        } else {
            emptyStore()
        }
        if (!config.useCaching) {
            SparqlEndpoint(MutableStore(base))
        } else {
            CachingSparqlEndpoint(ObservableStore(base))
        }
    }

    override suspend fun onSelectQueryRequest(query: String): Result<SelectResponse> {
        return inner.onSelectQueryRequest(query)
    }

    override suspend fun onUpdateQueryRequest(request: UpdateRequest): Result<Unit> {
        return inner.onUpdateQueryRequest(request)
    }

}
