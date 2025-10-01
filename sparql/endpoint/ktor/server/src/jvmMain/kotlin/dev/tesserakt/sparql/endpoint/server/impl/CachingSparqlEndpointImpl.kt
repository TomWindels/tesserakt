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


internal class CachingSparqlEndpointImpl(
    /**
     * The store that contains the quads. Updates received by this endpoint are reflected in this instance.
     *
     * This store instance is observable. This property is used to keep query caches up-to-date, allowing changes to
     *  propagate to these caches upon query re-execution.
     */
    private val store: ObservableStore,
    /**
     * The lock guarding the [store] field. When interacting with the store directly, the lock should be held by the
     *  interacting coroutine.
     *
     * If the [store] is expected to be mutated outside of this endpoint, a shared lock should be passed as an argument,
     *  ensuring the endpoint and the external logic does not mutate the store at the same time.
     */
    // could be a RW lock, but doesn't seem to exist in coroutines (yet)
    private val storeLock: Mutex = Mutex(),
    cacheSize: Int,
) : SparqlEndpoint {

    /**
     * The lock guarding the [queryCache] field. When interacting with the map itself, or with any evaluation found
     *  inside the map, the lock should be held by the interacting coroutine.
     */
    private val queryCacheLock = Mutex()

    private val queryCache = LRUCache<Query<Bindings>, DeferredOngoingQueryEvaluation<Bindings>>(cacheSize)

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
            SelectResponse(compiled, evaluation.results)
        }
    }

    override suspend fun onUpdateQueryRequest(request: UpdateRequest): Result<Unit> = runCatching {
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
                    // ensuring we don't leak these states through the ongoing store instance
                    queryCache.forEach {
                        it.value.unsubscribe(store)
                    }
                    queryCache.clear()
                }
            }
        }
    }

}
