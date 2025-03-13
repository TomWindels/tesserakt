package dev.tesserakt.sparql.runtime.stream

import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline

@JvmInline
value class StreamWithIndex<I : Any>(private val parent: Stream<I>): Stream<Pair<Int, I>> {

    class Iter<I>(
        private val iterator: Iterator<I>
    ): Iterator<Pair<Int, I>> {

        private var i = -1
        private var next = getNext()

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = getNext()
            return next != null
        }

        override fun next(): Pair<Int, I> {
            val current = next ?: getNext() ?: throw NoSuchElementException()
            next = null
            return i to current
        }

        private fun getNext() = if (iterator.hasNext()) {
            ++i
            iterator.next()
        } else null

    }

    override val cardinality: Cardinality
        get() = parent.cardinality

    override val description: String
        get() = "WithIndex[${parent.description}]"

    override fun supportsEfficientIteration(): Boolean = parent.supportsEfficientIteration()

    override fun iterator(): Iterator<Pair<Int, I>> {
        return Iter(iterator = parent.iterator())
    }

}
