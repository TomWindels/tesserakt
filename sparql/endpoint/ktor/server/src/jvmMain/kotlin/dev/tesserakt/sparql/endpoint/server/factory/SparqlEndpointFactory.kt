package dev.tesserakt.sparql.endpoint.server.factory

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.endpoint.server.SparqlEndpoint
import dev.tesserakt.sparql.endpoint.server.impl.CachingSparqlEndpointImpl
import dev.tesserakt.sparql.endpoint.server.impl.SparqlEndpointImpl
import kotlinx.coroutines.sync.Mutex

/**
 * Creates a [SparqlEndpoint] instance backed by an [ObservableStore]. As the instance allows for observations, caches
 *  can be created for requested queries, with cache updates deferred until getting new requests for the same query.
 *
 * @param store The store that contains the quads. Updates received by this endpoint are reflected in this instance.
 *  This store instance is observable. This property is used to keep query caches up-to-date, allowing changes to
 *  propagate to these caches upon query re-execution.
 * @param cacheSize The number of queries that should be cached (>= 0)
 * @param lock The lock guarding the [store] field. When interacting with the store directly, the lock should be held
 *  by the interacting coroutine. If the [store] is expected to be mutated outside of this endpoint, a shared lock
 *  should be passed as an argument, ensuring the endpoint and the external logic does not mutate the store at the same
 *  time.
 */
fun SparqlEndpoint(
    store: ObservableStore = ObservableStore(),
    cacheSize: Int = 3,
    lock: Mutex = Mutex(),
): SparqlEndpoint = when {
    cacheSize == 0 -> SparqlEndpointImpl(store = store, storeLock = lock)
    cacheSize > 0 ->  CachingSparqlEndpointImpl(store = store, storeLock = lock, cacheSize = cacheSize)
    else ->           throw IllegalArgumentException("A cache size of $cacheSize is invalid (should be >= 0)")
}

/**
 * Creates a [SparqlEndpoint] instance backed by a [MutableStore]. As the instance does not allow for observations, any
 *  requested query is not cached (changes cannot be detected and so caches cannot be kept up-to-date accordingly).
 *
 * @param store The store that contains the quads. Updates received by this endpoint are reflected in this instance.
 * @param lock The lock guarding the [store] field. When interacting with the store directly, the lock should be held by
 *  the interacting coroutine. If the [store] is expected to be mutated outside of this endpoint, a shared lock should
 *  be passed as an argument, ensuring the endpoint and the external logic does not mutate the store at the same time.
 */
fun SparqlEndpoint(store: MutableStore, lock: Mutex = Mutex()): SparqlEndpoint =
    SparqlEndpointImpl(store = store, storeLock = lock)
