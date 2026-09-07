package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.types.impl.StoreImpl
import dev.tesserakt.types.SizeAwareIterator
import dev.tesserakt.types.SuspendingIterator
import dev.tesserakt.types.forEach

fun Iterable<Quad>.toStore(): Store {
    return when (this) {
        is Collection<Quad> -> Store(this)
        else -> StoreImpl(toMutableSet())
    }
}

/**
 * Consumes `this` [Iterator], creating a [Store] that contains all (remaining) [Quad]s.
 */
fun Iterator<Quad>.toStore(capacityHint: Int? = null): Store {
    val sizeHint = when {
        capacityHint != null -> capacityHint
        this is SizeAwareIterator<*> -> this.estimatedSize
        // default parameter value
        else -> 10
    }
    // we use the platform-aware store factory method, so we
    //  can use concurrency if possible
    return Store(this.asIterable(), sizeHint = sizeHint)
}

private fun Iterator<Quad>.asIterable(): Iterable<Quad> {
    return object: Iterable<Quad> {
        override fun iterator(): Iterator<Quad> {
            return this@asIterable
        }
    }
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
