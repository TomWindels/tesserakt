package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.rdf.types.factory.emptyStore
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.factory.SparqlEndpoint

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
        // we can get away with using a simpler 'MutableStore' if no caching is required - the actual updates happening
        //  are not relevant for anything else
        when {
            config.cacheSize > 0 -> {
                SparqlEndpoint(ObservableStore(base), cacheSize = config.cacheSize)
            }
            config.cacheSize == 0 -> {
                SparqlEndpoint(MutableStore(base))
            }
            else -> throw IllegalStateException("Invalid cache configuration!")
        }
    }

    override suspend fun onSelectQueryRequest(query: String): Result<SelectResponse> {
        return inner.onSelectQueryRequest(query)
    }

    override suspend fun onUpdateQueryRequest(request: UpdateRequest): Result<Unit> {
        return inner.onUpdateQueryRequest(request)
    }

}
