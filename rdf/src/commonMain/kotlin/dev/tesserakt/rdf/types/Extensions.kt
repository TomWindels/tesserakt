package dev.tesserakt.rdf.types

import dev.tesserakt.SuspendingIterator
import dev.tesserakt.forEach
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.Store
import dev.tesserakt.rdf.types.impl.StoreImpl

fun Iterable<Quad>.toStore(): Store {
    return when (this) {
        is Collection<Quad> -> Store(this)
        else -> StoreImpl(toMutableSet())
    }
}

/**
 * Consumes `this` [Iterator], creating a [Store] that contains all (remaining) [Quad]s.
 */
fun Iterator<Quad>.toStore(): Store {
    return toStore(MutableStore())
}

/**
 * Consumes `this` [Iterator], adding all (remaining) [Quad]s into the [target] [MutableStore]. The [target] instance
 *  is returned.
 */
fun <S: MutableStore> Iterator<Quad>.toStore(target: S): S {
    forEach { target.add(it) }
    return target
}


/**
 * Consumes `this` [SuspendingIterator], creating a [Store] that contains all (remaining) [Quad]s.
 */
suspend fun SuspendingIterator<Quad>.toStore(): Store {
    return toStore(MutableStore())
}

/**
 * Consumes `this` [SuspendingIterator], adding all (remaining) [Quad]s into the [target] [MutableStore]. The [target]
 *  instance is returned.
 */
suspend fun <S: MutableStore> SuspendingIterator<Quad>.toStore(target: S): S {
    forEach { target.add(it) }
    return target
}
