package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.Cardinality

/**
 * Special variant of the [MappingStreamReduction] where exactly *1 element value* needs to be removed *once*
 */
class MappingStreamSingleElementReduction(
    private val source: Stream<Mapping>,
    private val removed: Mapping,
): Stream<Mapping> {

    // necessary type lower bound for the Counter type
    private class Iter(
        private val source: Iterator<Mapping>,
        private var remove: Mapping?
    ): Iterator<Mapping> {

        private var next: Mapping? = null

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): Mapping {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException()
        }

        private fun getNext(): Mapping? {
            while (source.hasNext()) {
                val result = source.next()
                val remove = remove ?: return result
                if (result.matches(remove)) {
                    // we no longer need to remove any element
                    this.remove = null
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

    override fun iterator(): Iterator<Mapping> {
        return Iter(source = source.iterator(), remove = removed)
    }

    override fun supportsReuse(): Boolean {
        return source.supportsReuse()
    }

}
