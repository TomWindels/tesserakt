package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.OneCardinality
import kotlin.jvm.JvmInline

@JvmInline
value class SingleStream<E: Any>(private val element: E): Stream<E>, OptimisedStream<E> {

    override val description: String
        get() = "Single element stream"

    override val cardinality: Cardinality
        get() = OneCardinality

    private class Iter<E: Any>(private var item: E?): Iterator<E> {

        override fun hasNext(): Boolean = item != null

        override fun next(): E {
            val current = item
            item = null
            return current ?: throw NoSuchElementException()
        }
    }

    override fun iterator(): Iterator<E> {
        return Iter(element)
    }

    override fun supportsReuse(): Boolean {
        return true
    }

}
