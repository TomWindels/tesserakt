package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality

/**
 * Special variant of [StreamChain]: only emits the elements from the [fallback] instance if [source] ends up emitting
 *  no elements of its own.
 */
class StreamChainEmpty<E: Any>(
    // mutable as the chain can be altered/combined during/after construction
    val source: Stream<E>,
    val fallback: Stream<E>,
): Stream<E> {

    private inner class Iter(
        private var iterator: Iterator<E>,
    ): Iterator<E> {

        private var emitted = false

        override fun hasNext(): Boolean {
            if (iterator.hasNext()) {
                return true
            }
            if (!emitted) {
                iterator = fallback.iterator()
                // we fake the idea that we've emitted - we simply don't want to restart the fallback iterator
                emitted = true
                return iterator.hasNext()
            }
            return false
        }

        override fun next(): E {
            emitted = true
            return iterator.next()
        }

    }

    override val cardinality: Cardinality
        get() = source.cardinality.let { if (it == ZeroCardinality) fallback.cardinality else it }

    override fun hasZeroCardinality() = source.hasZeroCardinality() && fallback.hasZeroCardinality()

    override fun supportsEfficientIteration(): Boolean {
        return source.supportsEfficientIteration() && fallback.supportsEfficientIteration()
    }

    override fun iterator(): Iterator<E> {
        return Iter(
            iterator = source.iterator()
        )
    }

    override fun supportsReuse(): Boolean {
        return source.supportsReuse() && fallback.supportsReuse()
    }

}
