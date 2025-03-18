package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

class StreamTransform<I: Any, O: Any>(
    private val source: OptimisedStream<I>,
    private val transform: (I) -> Stream<O>,
    override val cardinality: Cardinality,
): Stream<O> {

    private class Iter<I, O: Any>(
        private val source: Iterator<I>,
        private val transform: (I) -> Stream<O>,
        private var active: Iterator<O>,
    ): Iterator<O> {

        private var next: O? = null

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): O {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException()
        }

        private fun getNext(): O? {
            while (!active.hasNext() && source.hasNext()) {
                active = transform(source.next()).iterator()
            }
            if (active.hasNext()) {
                return active.next()
            }
            return null
        }

    }

    override val description: String
        get() = "Transform[${source.description}]"

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<O> {
        val iter = source.iterator()
        if (!iter.hasNext()) {
            return emptyIterator()
        }
        return Iter(
            source = iter,
            transform = transform,
            active = transform(iter.next()).iterator()
        )
    }

    override fun supportsReuse(): Boolean {
        return source.supportsReuse()
    }

}
