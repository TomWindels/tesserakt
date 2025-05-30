package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext

// a copy of binding identifier set
// not a value class as we have a custom equals check based on the contents of the `IntArray`, instead of reference equality
class TermIdentifierSet(private val ids: IntArray) {

    companion object {
        val Empty = TermIdentifierSet(ids = intArrayOf())
    }

    constructor(context: QueryContext, values: Iterable<Quad.Element>) :
            this(ids = values.map { context.resolveTerm(it) }.sorted().toIntArray())

    val size: Int
        get() = ids.size

    fun asIntIterable() = object: Iterable<Int> {
        override fun iterator(): IntIterator {
            return ids.iterator()
        }
    }

    operator fun get(index: Int): TermIdentifier {
        return TermIdentifier(id = ids[index])
    }

    operator fun contains(element: TermIdentifier): Boolean {
        val target = element.id
        // we can bin search, elements are sorted
        var min = 0
        var max = size - 1
        while (min <= max) {
            val mid = min + (max - min) / 2
            val current = ids[mid]
            when {
                target == current -> return true
                target < current -> max = mid - 1
                current < target -> min = mid + 1
            }
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TermIdentifierSet) {
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
