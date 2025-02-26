package dev.tesserakt.sparql.runtime.incremental.stream

internal object EmptyStream: Stream<Nothing> {

    object Iterator: kotlin.collections.Iterator<Nothing> {

        override fun hasNext(): Boolean {
            return false
        }

        override fun next(): Nothing {
            throw NoSuchElementException()
        }

    }

    override val cardinality: Int
        get() = 0

    override fun isEmpty() = true

    override fun iterator() = Iterator

}
