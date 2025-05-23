package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.evaluation.Mapping
import dev.tesserakt.sparql.runtime.evaluation.emptyMapping
import dev.tesserakt.sparql.util.Cardinality

class StreamMultiJoin(
    private val left: Stream<Mapping>,
    private val right: OptimisedStream<Mapping>,
): Stream<Mapping> {

    private class Iter(
        a: Iterable<Mapping>,
        // we have to repeat this one, so this one's kept
        private val b: Iterable<Mapping>,
    ): Iterator<Mapping> {

        private val source1 = a.iterator()
        private var source2 = b.iterator()

        private var left = source1.next()
        // the empty mapping is never read from, so this is not an error (instant `increment()` call)
        private var right: Mapping = emptyMapping()

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
         * Attempts to increment the various sources, represented as [right] and [left], returning true if successful,
         *  or false if the end of the input has been reached
         */
        private fun increment(): Boolean {
            if (!source1.hasNext() && !source2.hasNext()) {
                return false
            }
            if (!source2.hasNext()) {
                // wrapping around, incrementing the left side
                source2 = b.iterator()
                left = source1.next()
            }
            right = source2.next()
            return true
        }

    }

    override val description: String
        get() = "(${right.description}) ⨝ (${left.description})"

    override val cardinality: Cardinality
        get() = right.cardinality * left.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<Mapping> {
        return when {
            !left.supportsEfficientIteration() -> {
                Iter(a = left, b = left)
            }
            // both are optimised, so the smallest cardinality is the right element
            right.cardinality < left.cardinality -> {
                Iter(a = left, b = right)
            }
            else -> {
                Iter(a = right, b = left)
            }
        }
    }

    override fun supportsReuse(): Boolean {
        return left.supportsReuse() && right.supportsReuse()
    }

}
