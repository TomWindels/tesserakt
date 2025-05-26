package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.Store
import dev.tesserakt.rdf.types.impl.StoreImpl

fun Iterable<Quad>.toStore(): Store {
    return when (this) {
        is Collection<Quad> -> Store(this)
        else -> StoreImpl(toMutableSet())
    }
}

fun Iterator<Quad>.consume(target: MutableStore = MutableStore()): MutableStore {
    forEach { target.add(it) }
    return target
}
