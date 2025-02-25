package dev.tesserakt.sparql.runtime.incremental.iterable

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.types.Counter

internal class RemoveIterable(
    private val source: Iterable<Mapping>,
    removed: Iterable<Mapping>
): Iterable<Mapping> {

    private class Iter(
        private val source: Iterator<Mapping>,
        private val remove: Counter<Mapping>
    ): Iterator<Mapping> {

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
            while (source.hasNext()) {
                val next = source.next()
                if (next in remove) {
                    remove.decrement(next)
                    continue
                }
                return next
            }
            return next
        }

    }

    private val counter = Counter(removed)

    override fun iterator(): Iterator<Mapping> {
        return Iter(source = source.iterator(), remove = counter.clone())
    }

}
