package dev.tesserakt.sparql.runtime.incremental.stream

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith

internal class StreamMultiJoin(
    private val left: Stream<Mapping>,
    private val right: Stream<Mapping>,
): Stream<Mapping> {

    private class Iter(
        a: Iterable<Mapping>,
        // we have to repeat this one, so this one's kept
        private val b: Iterable<Mapping>,
    ): Iterator<Mapping> {

        private val source1 = a.iterator()
        private var source2 = b.iterator()

        private var left = source1.next()
        private lateinit var right: Mapping

        private var next = getNext()

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
                if (left.compatibleWith(right)) {
                    return left + right
                }
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
                left = source1.next()
            }
            right = source2.next()
            return true
        }

    }

    override val cardinality: Int
        get() = left.cardinality * right.cardinality

    // there's no better way here
    private val _isEmpty by lazy { !iterator().hasNext() }

    override fun isEmpty() = _isEmpty

    init {
        require(!left.isEmpty() && !right.isEmpty())
    }

    override fun iterator(): Iterator<Mapping> {
        // iterating, using the smallest cardinality on the right (b), as that one's repeated as long as the left
        //  stream can produce; larger cardinality streams have a higher likelihood of actually skipping a lot of
        //  combinations (i.e. produced by another join), where unnecessary rechecks (through repeated iterations)
        //  should be avoided
        return if (left.cardinality < right.cardinality) {
            Iter(a = right, b = left)
        } else {
            Iter(a = left, b = right)
        }
    }

}
