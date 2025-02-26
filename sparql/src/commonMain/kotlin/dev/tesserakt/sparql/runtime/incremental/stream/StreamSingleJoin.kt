package dev.tesserakt.sparql.runtime.incremental.stream

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith

internal class StreamSingleJoin(
    private val left: Mapping,
    private val right: Stream<Mapping>,
): Stream<Mapping> {

    private class Iter(
        private val left: Mapping,
        // we have to repeat this one, so this one's kept
        private val source: Iterator<Mapping>,
    ): Iterator<Mapping> {

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
            if (!source.hasNext()) {
                return false
            }
            right = source.next()
            return true
        }

    }

    // there's no better way here
    private val _isEmpty by lazy { !iterator().hasNext() }

    override fun isEmpty() = _isEmpty

    override val cardinality: Int
        get() = right.cardinality

    init {
        require(right.isNotEmpty())
    }

    override fun iterator(): Iterator<Mapping> {
        return Iter(left = left, source = right.iterator())
    }

}
