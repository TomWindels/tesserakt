package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.util.Cardinality

class StreamSingleJoin(
    private val left: Mapping,
    private val right: Stream<Mapping>,
): Stream<Mapping> {

    private class Iter(
        private val left: Mapping,
        // we have to repeat this one, so this one's kept
        private val source: Iterator<Mapping>,
    ): Iterator<Mapping> {

        // the empty mapping is never read from, so this is not an error (instant `increment()` call)
        private lateinit var right: Mapping

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
            while (increment()) {
                val joined = left.join(right)
                if (joined != null) {
                    return joined
                }
            }
            return null
        }

        /**
         * Attempts to increment the various sources, represented as [left] and [right], returning true if successful,
         *  or false if the end of the input has been reached
         */
        private fun increment(): Boolean {
            if (!source.hasNext()) {
                return false
            }
            right = source.next()
            return true
        }

    }

    override val description: String
        get() = "(${right.description}) ⨝ ($left)"

    override val cardinality: Cardinality
        get() = right.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<Mapping> {
        return Iter(left = left, source = right.iterator())
    }

    override fun supportsReuse(): Boolean {
        return right.supportsReuse()
    }

}
