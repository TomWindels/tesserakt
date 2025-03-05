package dev.tesserakt.sparql.runtime.incremental.stream

import dev.tesserakt.sparql.runtime.incremental.types.Cardinality
import dev.tesserakt.sparql.runtime.incremental.types.ZeroCardinality

internal object EmptyStream: Stream<Nothing>, OptimisedStream<Nothing> {

    object Iterator: kotlin.collections.Iterator<Nothing> {

        override fun hasNext(): Boolean {
            return false
        }

        override fun next(): Nothing {
            throw NoSuchElementException()
        }

    }

    override val description: String
        get() = "Empty"

    override val cardinality: Cardinality
        get() = ZeroCardinality

    override fun iterator() = Iterator

}
