package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality

internal class StreamProduct<A: Any, B: Any>(
    private val left: Stream<A>,
    private val right: OptimisedStream<B>,
): Stream<Pair<A, B>> {

    private class Iter<A: Any, B: Any>(
        a: Iterable<A>,
        // we have to repeat this one, so this one's kept
        private val b: Iterable<B>,
    ): Iterator<Pair<A, B>> {

        private val source1 = a.iterator()
        private var source2 = b.iterator()

        private var left = if (source1.hasNext()) source1.next() else null
        private lateinit var right: B

        private var next: Pair<A, B>? = null

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): Pair<A, B> {
            val current = next ?: getNext()
            next = null
            return current ?: throw NoSuchElementException()
        }

        private fun getNext(): Pair<A, B>? {
            while (increment()) {
                return (left ?: return null) to right
            }
            return null
        }

        /**
         * Attempts to increment the various sources, represented as [left] and [right], returning true if successful,
         *  or false if the end of the input has been reached
         */
        private fun increment(): Boolean {
            if (!source1.hasNext() && !source2.hasNext()) {
                return false
            }
            if (!source2.hasNext()) {
                // wrapping around, incrementing the left side
                source2 = b.iterator()
                // we're wrapping around nothing, skipping
                if (!source2.hasNext()) {
                    return false
                }
                left = source1.next()
            }
            right = source2.next()
            return true
        }

    }

    override val description: String
        get() = "(${left.description}) ⨉ (${right.description})"

    override val cardinality: Cardinality = left.cardinality * right.cardinality

    override fun supportsEfficientIteration(): Boolean {
        return left.supportsEfficientIteration() && right.supportsEfficientIteration()
    }

    override fun iterator(): Iterator<Pair<A, B>> {
        return Iter(a = left, b = right)
    }

}
