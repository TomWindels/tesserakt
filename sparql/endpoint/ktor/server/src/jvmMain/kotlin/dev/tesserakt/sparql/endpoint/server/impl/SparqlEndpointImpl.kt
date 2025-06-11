package dev.tesserakt.sparql.endpoint.server.impl

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.endpoint.core.data.SelectResponse
import dev.tesserakt.sparql.endpoint.core.data.UpdateRequest
import dev.tesserakt.sparql.endpoint.server.SparqlEndpoint
import dev.tesserakt.sparql.query
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal class SparqlEndpointImpl(
    /**
     * The store that contains the quads. Updates received by this endpoint are reflected in this instance.
     */
    private val store: MutableStore,
    /**
     * The lock guarding the [store] field. When interacting with the store directly, the lock should be held by the
     *  interacting coroutine.
     *
     * If the [store] is expected to be mutated outside of this endpoint, a shared lock should be passed as an argument,
     *  ensuring the endpoint and the external logic does not mutate the store at the same time.
     */
    private val storeLock: Mutex = Mutex(),
) : SparqlEndpoint {

    override suspend fun onSelectQueryRequest(query: String): Result<SelectResponse> = runCatching {
        val compiled = Query.Select(query)
        // whilst querying, the store should not mutate
        val results = storeLock.withLock {
            store.query(compiled)
        }
        SelectResponse(compiled, results)
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
        }
    }

}
