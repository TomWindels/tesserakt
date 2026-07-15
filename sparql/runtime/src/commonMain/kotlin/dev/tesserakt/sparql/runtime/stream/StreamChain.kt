package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality

class StreamChain<E: Any>(
    // mutable as the chain can be altered/combined during/after construction
    val sources: MutableList<Stream<E>>
): Stream<E> {

    private class Iter<E: Any>(
        private val sources: Iterator<Stream<E>>
    ): Iterator<E> {

        private var src: Iterator<E>? = if (sources.hasNext()) sources.next().iterator() else null
        private var next = getNext()

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): E {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException()
        }

        private fun getNext(): E? {
            var src = src ?: return null
            while (!src.hasNext()) {
                if (!sources.hasNext()) {
                    this.src = null
                    return null
                }
                src = sources.next().iterator()
                this.src = src
            }
            return src.next()
        }

    }

    override val cardinality: Cardinality
        // we don't cache it as it's possible the list of sources grow
        get() = sources.fold(ZeroCardinality) { total, item -> total + item.cardinality }

    override fun hasZeroCardinality() = sources.all { it.hasZeroCardinality() }

    override fun supportsEfficientIteration(): Boolean {
        return sources.all { it.supportsEfficientIteration() }
    }

    override fun iterator(): Iterator<E> {
        return Iter(sources.iterator())
    }

    override fun supportsReuse(): Boolean {
        return sources.all { it.supportsReuse() }
    }

}
