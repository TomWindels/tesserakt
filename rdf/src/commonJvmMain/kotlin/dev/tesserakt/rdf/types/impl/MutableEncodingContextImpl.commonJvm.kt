package dev.tesserakt.rdf.types.impl

import dev.tesserakt.concurrent.SimpleConcurrentList
import java.util.concurrent.ConcurrentHashMap

internal actual fun ConcurrentMutableEncodingContextImpl(): MutableEncodingContextImpl {
    return MutableEncodingContextImpl(
        encoder = ConcurrentHashMap(),
        decoder = SimpleConcurrentList(),
    )
}