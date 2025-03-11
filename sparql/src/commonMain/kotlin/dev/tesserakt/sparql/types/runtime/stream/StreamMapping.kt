package dev.tesserakt.sparql.types.runtime.stream

import dev.tesserakt.sparql.types.util.Cardinality

internal class StreamMapping<I: Any, O: Any>(
    private val source: Stream<I>,
    private val transform: (I) -> O
): Stream<O> {

    private class Iter<I, O: Any>(
        private val source: Iterator<I>,
        private val transform: (I) -> O,
    ): Iterator<O> {

        private var next = getNext()

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
            if (source.hasNext()) {
                return transform(source.next())
            }
            return null
        }

    }

    override val description: String
        get() = "Mapping[${source.description}]"

    override val cardinality: Cardinality
        get() = source.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return source.supportsEfficientIteration()
    }

    override fun iterator(): Iterator<O> {
        return Iter(source.iterator(), transform)
    }

}
