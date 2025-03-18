package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

class StreamFilter<I: Any>(
    private val source: Stream<I>,
    private val predicate: (I) -> Boolean
): Stream<I> {

    private class Iter<I: Any>(
        private val source: Iterator<I>,
        private val predicate: (I) -> Boolean,
        private var next: I?,
    ): Iterator<I> {

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): I {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException()
        }

        private fun getNext(): I? {
            while (source.hasNext()) {
                val next = source.next()
                if (predicate(next)) {
                    return next
                }
            }
            return null
        }

    }

    override val description: String
        get() = "Filter[${source.description}]"

    // worst case, none filtered out at all
    override val cardinality: Cardinality
        get() = source.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<I> {
        val iter = source.iterator()
        var first: I? = null
        while (iter.hasNext()) {
            first = iter.next()
            if (predicate(first)) {
                break
            }
        }
        if (first == null || !predicate(first)) {
            return emptyIterator()
        }
        return Iter(
            source = iter,
            predicate = predicate,
            next = first
        )
    }

    override fun supportsReuse(): Boolean {
        return source.supportsReuse()
    }

}
