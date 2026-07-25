package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality

class FlatMapStream<E : Any>(
    private val source: Iterable<Iterable<E>>,
    override val cardinality: Cardinality,
): OptimisedStream<E> {

    constructor(
        source: Iterable<Collection<E>>
    ): this(
        source = source,
        cardinality = Cardinality(source.sumOf { it.size }),
    )

    private class Iter<E : Any>(
        private val sources: Iterator<Iterable<E>>
    ): Iterator<E> {

        private var src = if (sources.hasNext()) sources.next().iterator() else null
        private var next: E? = null

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): E {
            val n = next ?: getNext() ?: throw NoSuchElementException()
            next = null
            return n
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

    override fun iterator(): Iterator<E> {
        return Iter(sources = source.iterator())
    }

    override fun hasZeroCardinality(): Boolean {
        return cardinality == ZeroCardinality
    }

    override fun supportsReuse(): Boolean {
        return true
    }

}
