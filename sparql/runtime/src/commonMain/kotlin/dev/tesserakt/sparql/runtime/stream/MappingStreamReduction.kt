package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.runtime.evaluation.mapping.HashableMapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.evaluation.mapping.hashable
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.Counter

class MappingStreamReduction(
    private val source: Stream<Mapping>,
    removed: Iterable<Mapping>
): Stream<Mapping> {

    private class Iter(
        private val source: Iterator<Mapping>,
        private val remove: Counter<HashableMapping>
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
                if (remove.count == 0) {
                    return result
                }
                val hashed = result.hashable()
                if (hashed in remove) {
                    remove.decrement(hashed)
                    continue
                }
                return result
            }
            return null
        }

    }

    private val counter = Counter(removed.map { it.hashable() })

    override val cardinality: Cardinality
        // not removing the dropped ones from the cardinality, as it's not guaranteed they're present in the first place
        get() = source.cardinality

    override fun hasZeroCardinality(): Boolean {
        return source.hasZeroCardinality()
    }

    override fun supportsEfficientIteration(): Boolean {
        return false
    }

    override fun iterator(): Iterator<Mapping> {
        return Iter(source = source.iterator(), remove = counter.clone())
    }

    override fun supportsReuse(): Boolean {
        return source.supportsReuse()
    }

}
