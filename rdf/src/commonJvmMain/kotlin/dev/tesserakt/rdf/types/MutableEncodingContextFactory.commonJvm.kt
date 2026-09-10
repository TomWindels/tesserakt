package dev.tesserakt.rdf.types

import dev.tesserakt.concurrent.SimpleConcurrentList
import dev.tesserakt.rdf.types.impl.MutableEncodingContextImpl
import java.util.concurrent.ConcurrentHashMap

/**
 * Constructs a new [dev.tesserakt.rdf.types.MutableEncodingContext] instance with the
 *  various [MutableEncodingContextBuilder] properties satisfied.
 */
actual fun MutableEncodingContext(builder: MutableEncodingContextBuilder.() -> Unit): MutableEncodingContext {
    val opts = MutableEncodingContextBuilder().apply(builder)
    return when {
        opts.concurrent -> {
            MutableEncodingContextImpl(
                encoder = ConcurrentHashMap(opts.initialCapacity),
                decoder = SimpleConcurrentList(opts.initialCapacity),
            )
        }
        else -> {
            MutableEncodingContextImpl(opts.initialCapacity)
        }
    }
}
