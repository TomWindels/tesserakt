package dev.tesserakt.sparql.runtime.incremental.iterable

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith

internal class SingleJoinIterable(
    private val left: Mapping,
    private val right: Iterable<Mapping>,
): Iterable<Mapping> {

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

    override fun iterator(): Iterator<Mapping> {
        return if (right.isEmpty()) {
            emptyIterator()
        } else {
            Iter(left = left, source = right.iterator())
        }
    }

}
