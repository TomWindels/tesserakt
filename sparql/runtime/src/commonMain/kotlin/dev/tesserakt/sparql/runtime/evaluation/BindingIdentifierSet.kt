package dev.tesserakt.sparql.runtime.evaluation

import kotlin.jvm.JvmInline

// not a value class as we have a custom equals check based on the contents of the `IntArray`, instead of reference equality
class BindingIdentifierSet(private val ids: IntArray) {

    constructor(context: QueryContext, names: Iterable<String>) :
            this(ids = names.distinct().map { context.resolveBinding(it) }.sorted().toIntArray())

    @JvmInline
    value class IdIterator(private val iterator: IntIterator): Iterator<BindingIdentifier> {
        override fun hasNext() = iterator.hasNext()
        override fun next() = BindingIdentifier(iterator.next())
    }

    val size: Int
        get() = ids.size

    fun asIntIterable() = object: Iterable<Int> {
        override fun iterator(): IntIterator {
            return ids.iterator()
        }
    }

    fun asIdIterable() = object: Iterable<BindingIdentifier> {
        override fun iterator() = IdIterator(ids.iterator())
    }

    operator fun contains(element: Int): Boolean {
        // we can bin search, elements are sorted
        var min = 0
        var max = size - 1
        while (min <= max) {
            val mid = min + (max - min) / 2
            val current = ids[mid]
            when {
                element == current -> return true
                element < current -> max = mid - 1
                current < element -> min = mid + 1
            }
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BindingIdentifierSet) {
            return false
        }
        if (this.size != other.size) {
            return false
        }
        return ids.contentEquals(other.ids)
    }

    override fun hashCode(): Int {
        return ids.contentHashCode()
    }

}
