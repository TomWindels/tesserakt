package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter

class StreamReduction<E: Any>(
    private val source: Stream<E>,
    removed: Iterable<E>
): Stream<E> {

    private class Iter<E>(
        private val source: Iterator<E>,
        private val remove: Counter<E>
    ): Iterator<E> {

        private var next: E? = null

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
            while (source.hasNext()) {
                val result = source.next()
                if (result in remove) {
                    remove.decrement(result)
                    continue
                }
                return result
            }
            return null
        }

    }

    private val counter = Counter(removed)

    override val description: String
        get() = "Reduction[${source.description}]"

    override val cardinality: Cardinality
        // not removing the dropped ones from the cardinality, as it's not guaranteed they're present in the first place
        get() = source.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<E> {
        return Iter(source = source.iterator(), remove = counter.clone())
    }

}
