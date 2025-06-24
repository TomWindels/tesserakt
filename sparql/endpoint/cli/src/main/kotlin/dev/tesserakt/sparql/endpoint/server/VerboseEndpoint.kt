package dev.tesserakt.sparql.endpoint.server

import dev.tesserakt.rdf.serialization.common.FileDataSource
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.rdf.types.factory.emptyStore
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.impl.CachingSparqlEndpoint
import dev.tesserakt.sparql.endpoint.server.impl.SparqlEndpoint

/**
 * A [SparqlEndpoint] decorator, similar to [Endpoint], with the addition of making the various operations more verbose
 */
class VerboseEndpoint(config: EndpointConfig) : SparqlEndpoint {

    // whilst we don't use the store for anything here, it's useful for logging
    private val store = run {
        val base = if (config.start != null) {
            TriGSerializer
                .also { println("Setting up the initial data...") }
                .deserialize(FileDataSource(config.start))
                .consumeVerbose()
                .also { println(" done!") }
        } else {
            emptyStore()
        }
        ObservableStore(base)
    }
    private val inner = if (!config.useCaching) {
        SparqlEndpoint(store)
    } else {
        CachingSparqlEndpoint(store)
    }

    private var added = 0
    private var deleted = 0

    private val listener = object : ObservableStore.Listener {
        override fun onQuadAdded(quad: Quad) {
            ++added
        }

        override fun onQuadRemoved(quad: Quad) {
            ++deleted
        }
    }

    init {
        require(config.verbose)
        store.addListener(listener)
    }

    override suspend fun onSelectQueryRequest(query: String): Result<SelectResponse> {
        println("Got a select query request on the store (size ${store.size} quad(s)): ```\n$query\n```")
        val result = inner.onSelectQueryRequest(query)
        result.onSuccess { println("Query generated ${it.results.bindings.size} binding(s)") }
        return result
    }

    override suspend fun onUpdateQueryRequest(request: UpdateRequest): Result<Unit> {
        added = 0
        deleted = 0
        println("Got an update query request:\n * ${request.additions.size} addition(s)\n * ${request.deletions.size} deletion(s)")
        val t = inner.onUpdateQueryRequest(request)
        println("Callback calls: $added x added, $deleted x deleted")
        return t
    }

    private fun Iterator<Quad>.consumeVerbose(): Store {
        val iLimit = 10_000
        val jLimit = 10

        var i = 0
        var j = 0

        val result = MutableStore()
        forEach {
            if (j >= jLimit) {
                println(" ${result.size}")
                j = 0
            }
            ++i
            if (i >= iLimit) {
                i = 0
                ++j
                print('.')
            }
            result.add(it)
        }
        return result
    }

}
