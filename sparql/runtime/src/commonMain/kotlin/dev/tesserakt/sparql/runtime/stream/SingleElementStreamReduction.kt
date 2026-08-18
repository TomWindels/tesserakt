package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

/**
 * Special variant of the [StreamReduction] where exactly *1 element value* needs to be removed *once*
 */
class SingleElementStreamReduction<E: Any>(
    private val source: Stream<E>,
    private val removed: E
): Stream<E> {

    // necessary type lower bound for the Counter type
    private class Iter<E : Any>(
        private val source: Iterator<E>,
        private var remove: E?
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
                if (result == remove) {
                    // we no longer need to remove any element
                    remove = null
                    continue
                }
                return result
            }
            return null
        }

    }

    override val cardinality: Cardinality
        // not removing the dropped ones from the cardinality, as it's not guaranteed they're present in the first place
        get() = source.cardinality

    override fun hasZeroCardinality(): Boolean {
        return source.hasZeroCardinality()
    }

    override fun supportsEfficientIteration(): Boolean {
        // we're simple enough in this case compared to the more complete single item stream reduction
        return source.supportsEfficientIteration()
    }

    override fun iterator(): Iterator<E> {
        return Iter(source = source.iterator(), remove = removed)
    }

    override fun supportsReuse(): Boolean {
        return source.supportsReuse()
    }

}
