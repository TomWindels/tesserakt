package dev.tesserakt.sparql.types.runtime.stream

import dev.tesserakt.sparql.types.util.Cardinality

internal class StreamMappingNullable<I: Any, O: Any>(
    private val source: Stream<I>,
    private val transform: (I) -> O?
): Stream<O> {

    private class Iter<I, O: Any>(
        private val source: Iterator<I>,
        private val transform: (I) -> O?,
        private var next: O?,
    ): Iterator<O> {

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
            while (source.hasNext()) {
                val transformed = transform(source.next()) ?: continue
                return transformed
            }
            return null
        }

    }

    override val description: String
        get() = "Mapping?[${source.description}]"

    // worst case, no nulls at all
    override val cardinality: Cardinality
        get() = source.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<O> {
        val iter = source.iterator()
        var transformed: O? = null
        while (iter.hasNext()) {
            transformed = transform(iter.next()) ?: continue
            break
        }
        if (transformed == null) {
            return emptyIterator()
        }
        return Iter(
            source = iter,
            transform = transform,
            next = transformed
        )
    }

}
