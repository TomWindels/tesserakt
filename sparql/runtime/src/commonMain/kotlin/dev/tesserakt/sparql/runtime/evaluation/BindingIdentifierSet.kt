package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext

// not a value class as we have a custom equals check based on the contents of the `IntArray`, instead of reference equality
class BindingIdentifierSet(private val ids: IntArray) {

    constructor(context: QueryContext, names: Iterable<String>) :
            this(ids = names.distinct().map { context.resolveBinding(it) }.sorted().toIntArray())

    constructor(context: QueryContext, names: Set<String>) :
            this(ids = names.map { context.resolveBinding(it) }.sorted().toIntArray())

    val size: Int
        get() = ids.size

    fun asIntIterable() = object: Iterable<Int> {
        override fun iterator(): IntIterator {
            return ids.iterator()
        }
    }

    operator fun get(index: Int): BindingIdentifier {
        return BindingIdentifier(id = ids[index])
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

    override fun toString() = ids.joinToString(prefix = "BindingIdentifierSet {", postfix = "}")

    companion object {
        val EMPTY = BindingIdentifierSet(ids = intArrayOf())
    }

}
