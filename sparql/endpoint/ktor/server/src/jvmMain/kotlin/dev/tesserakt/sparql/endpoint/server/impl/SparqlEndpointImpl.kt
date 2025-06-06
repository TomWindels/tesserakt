package dev.tesserakt.sparql.endpoint.server.impl

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.SparqlEndpoint
import dev.tesserakt.sparql.evaluation.DeferredOngoingQueryEvaluation
import dev.tesserakt.sparql.queryDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal class SparqlEndpointImpl(private val store: ObservableStore) : SparqlEndpoint {

    /**
     * The lock guarding the [queryCache] field. When interacting with the map itself, or with any evaluation found
     *  inside the map, the lock should be held by the interacting coroutine.
     */
    private val queryCacheLock = Mutex()

    /**
     * The lock guarding the [store] field. When interacting with the store directly, the lock should be held by the
     *  interacting coroutine.
     */
    // could be a RW lock, but doesn't seem to exist in coroutines (yet)
    private val storeLock = Mutex()

    private val queryCache = mutableMapOf<Query<Bindings>, DeferredOngoingQueryEvaluation<Bindings>>()

    override suspend fun onSelectQueryRequest(query: String): Result<SelectResponse> = runCatching {
        val compiled = Query.Select(query)
        // the entire query evaluation logic has to be put behind the lock, as it's possible for two concurrent
        //  requests targeting the same cached query to update the deferred results otherwise
        queryCacheLock.withLock {
            val evaluation = queryCache.getOrPut(compiled) {
                // as we're creating a new query state from scratch, we're required to lock the underlying store,
                //  as the data is being added to the queue
                storeLock.withLock {
                    store.queryDeferred(compiled)
                }
            }
            SelectResponse(compiled, evaluation)
        }
    }

    override suspend fun onUpdateQueryRequest(query: String): Result<Unit> = runCatching {
        val request = UpdateRequest.parse(query)
        if (request.additions.isEmpty() && request.deletions.isEmpty()) {
            // early bailout - no locks required
            return@runCatching
        }
        storeLock.withLock {
            store.addAll(request.additions)
            store.removeAll(request.deletions)
            // small optimisation: if the UPDATE causes all data to be removed, it's much faster to clear
            //  any existing, outdated, queries and re-evaluate them from scratch than it is to
            //  first process all deletions from the outdated state
            if (store.isEmpty()) {
                queryCacheLock.withLock {
                    queryCache.clear()
                }
            }
        }
    }

}
