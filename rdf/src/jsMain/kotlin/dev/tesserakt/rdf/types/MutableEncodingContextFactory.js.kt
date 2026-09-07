package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.MutableEncodingContextImpl

actual fun MutableEncodingContext(builder: MutableEncodingContextBuilder.() -> Unit): MutableEncodingContext {
    val opts = MutableEncodingContextBuilder().apply(builder)
    // no concurrency on JS
    return MutableEncodingContextImpl(opts.initialCapacity)
}
