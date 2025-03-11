package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

internal class StreamChain<E: Any>(
    val source1: Stream<E>,
    val source2: Stream<E>,
): Stream<E> {

    private class Iter<E: Any>(
        private val source1: Iterator<E>,
        private val source2: Iterator<E>
    ): Iterator<E> {

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
            if (source1.hasNext()) {
                return source1.next()
            }
            if (source2.hasNext()) {
                return source2.next()
            }
            return null
        }

    }

    override val description: String
        get() = "${source1.description}, ${source2.description}"

    override val cardinality: Cardinality
        get() = source1.cardinality + source2.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return source1.supportsEfficientIteration() && source2.supportsEfficientIteration()
    }

    override fun iterator(): Iterator<E> {
        return Iter(source1 = source1.iterator(), source2 = source2.iterator())
    }

}
