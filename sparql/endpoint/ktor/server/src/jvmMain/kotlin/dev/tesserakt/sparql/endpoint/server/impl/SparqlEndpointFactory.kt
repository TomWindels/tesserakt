package dev.tesserakt.sparql.endpoint.server.impl

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.sparql.endpoint.server.SparqlEndpoint
import kotlinx.coroutines.sync.Mutex

fun SparqlEndpoint(store: ObservableStore = ObservableStore()): SparqlEndpoint = CachingSparqlEndpointImpl(store)

/**
 * Creates a [SparqlEndpoint] instance backed by a [MutableStore]. As the instance does not allow for observations, any
 *  requested query is not cached (changes cannot be detected and so caches cannot be kept up-to-date accordingly).
 *
 * @param store The store that contains the quads. Updates received by this endpoint are reflected in this instance.
 * @param lock The lock guarding the [store] field. When interacting with the store directly, the lock should be held by
 *  the interacting coroutine. If the [store] is expected to be mutated outside of this endpoint, a shared lock should
 *  be passed as an argument, ensuring the endpoint and the external logic does not mutate the store at the same time.
 */
fun SparqlEndpoint(store: MutableStore, lock: Mutex = Mutex()): SparqlEndpoint = SparqlEndpointImpl(store = store, storeLock = lock)
