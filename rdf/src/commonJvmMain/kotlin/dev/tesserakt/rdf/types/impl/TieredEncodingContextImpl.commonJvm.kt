package dev.tesserakt.rdf.types.impl

import dev.tesserakt.concurrent.SimpleConcurrentList
import dev.tesserakt.rdf.types.MutableEncodingContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Creates a new [TieredEncodingContextImpl] instance that uses concurrent datastructures to support
 */
@OptIn(ExperimentalAtomicApi::class)
actual fun TieredEncodingContextImpl.Companion.concurrent(): TieredEncodingContextImpl {
    return TieredEncodingContextImpl(
        fallback = MutableEncodingContext { concurrent = true },
        luts = Array(4) { TieredEncodingContextImpl.DomainCollection(lut = ConcurrentHashMap(), reverse = SimpleConcurrentList()) },
        factory = {
            TieredEncodingContextImpl.PathCollection(
                paths = ConcurrentHashMap(),
                reverse = SimpleConcurrentList()
            )
        }
    )
}
