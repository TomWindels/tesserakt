package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality

object EmptyStream: Stream<Nothing>, OptimisedStream<Nothing> {

    object Iterator: kotlin.collections.Iterator<Nothing> {

        override fun hasNext(): Boolean {
            return false
        }

        override fun next(): Nothing {
            throw NoSuchElementException()
        }

    }

    override val cardinality: Cardinality
        get() = ZeroCardinality

    override fun hasZeroCardinality(): Boolean {
        return true
    }

    override fun iterator() = Iterator

    override fun supportsReuse(): Boolean {
        return true
    }

}
