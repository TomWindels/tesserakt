package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.MutableEncodingContextImpl

actual fun MutableEncodingContext(builder: MutableEncodingContextBuilder.() -> Unit): MutableEncodingContext {
    val opts = MutableEncodingContextBuilder().apply(builder)
    if (opts.concurrent) {
        throw UnsupportedOperationException("Concurrency is currently not supported on Kotlin/Native!")
    }
    return MutableEncodingContextImpl(opts.initialCapacity)
}
