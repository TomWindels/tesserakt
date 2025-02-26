package dev.tesserakt.sparql.runtime.incremental.stream

import kotlin.jvm.JvmInline

@JvmInline
internal value class SingleStream<E: Any>(private val element: E): Stream<E> {

    override val cardinality: Int
        get() = 1

    private class Iter<E: Any>(private var item: E?): Iterator<E> {

        override fun hasNext(): Boolean = item != null

        override fun next(): E {
            val current = item
            item = null
            return current ?: throw NoSuchElementException()
        }
    }

    override fun isEmpty() = false

    override fun iterator(): Iterator<E> {
        return Iter(element)
    }

}
