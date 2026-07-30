package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.util.SortedCounter

// not a value class as we have a custom equals check based on the contents of the `IntArray`, instead of reference equality
// TODO(perf):
//  make this an interface, so support for 'small identifier sets' is possible
//  when 'binding scopes' become a thing, make optimal use of the fact that binding IDs within a scope are in the 0..31
//  range by making a 'LocalBindingIdentifierSet' type that uses a single Int to contain a bitmask of all IDs contained
//  within the set; this then implements this interface
//  to clarify what scope a local identifier set belongs to, X MSBs could be used to encode a 'scope id'; this way,
//  incorrectly associating one binding identifier set with another without 'scope mapping lookup' can be
//  detected (e.g. when creating a combined or union version of two identifier sets)
class BindingIdentifierSet
/**
 * The primary way of creating a binding identifier set.
 *
 * IMPORTANT: the [ids] have to be sorted, as binary search is used to look through the set of IDs!
 */
constructor(
    private val ids: IntArray
) {

    constructor(ids: Iterable<Int>) :
            this(ids = ids.distinct().sorted().toIntArray())

    constructor(context: QueryContext, names: Iterable<String>) :
            this(ids = names.distinct().map { context.resolveBinding(it) }.sorted().toIntArray())

    constructor(context: QueryContext, names: Set<String>) :
            this(ids = names.map { context.resolveBinding(it) }.sorted().toIntArray())

    val size: Int
        get() = ids.size

    fun isEmpty(): Boolean {
        return size == 0
    }

    fun isNotEmpty(): Boolean {
        return size != 0
    }

    fun asIntIterable() = object: Iterable<Int> {
        override fun iterator(): IntIterator {
            return ids.iterator()
        }
    }

    fun iterator(): IntIterator {
        return ids.iterator()
    }

    fun intersectSize(other: BindingIdentifierSet): Int {
        if (this.size == 0 || other.size == 0) {
            return 0
        }
        val smallest: BindingIdentifierSet
        val largest: BindingIdentifierSet
        if (this.size <= other.size) {
            smallest = this
            largest = other
        } else {
            smallest = other
            largest = this
        }
        return smallest.asIntIterable().count { largest.contains(it) }
    }

    fun unionSize(other: BindingIdentifierSet): Int {
        // we counted our intersection double
        return this.size + other.size - this.intersectSize(other)
    }

    fun intersect(other: BindingIdentifierSet): BindingIdentifierSet {
        if (this.size == 0 || other.size == 0) {
            return EMPTY
        }
        val smallest: BindingIdentifierSet
        val largest: BindingIdentifierSet
        if (this.size <= other.size) {
            smallest = this
            largest = other
        } else {
            smallest = other
            largest = this
        }
        val commonCount = smallest.asIntIterable().count { largest.contains(it) }
        val iter = smallest.iterator()
        return BindingIdentifierSet(
            ids = IntArray(commonCount) {
                var result = iter.next()
                while (result !in largest) {
                    result = iter.next()
                }
                result
            }
        )
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

    operator fun contains(elements: BindingIdentifierSet): Boolean {
        if (elements.size > this.size) {
            return false
        }
        return elements.asIntIterable().all { it in this }
    }

    operator fun plus(other: BindingIdentifierSet): BindingIdentifierSet {
        // using the sorted counter type as that is a sorted map for the individual keys, meaning that we get
        // * the sorted ID order we require
        // * a distinct set of IDs present in either identifier set
        val temp = SortedCounter<Int>()
        this.asIntIterable().forEach { temp.increment(it) }
        other.asIntIterable().forEach { temp.increment(it) }
        val iter = temp.iterator()
        val ids = IntArray(temp.size) { iter.next().key }
        return BindingIdentifierSet(ids)
    }

    operator fun minus(other: BindingIdentifierSet): BindingIdentifierSet {
        val size = this.size - this.intersectSize(other)
        val ids = IntArray(size)
        val iter = this.ids.iterator()
        repeat(size) { i ->
            var next = iter.next()
            while (next in other) {
                next = iter.next()
            }
            ids[i] = next
        }
        return BindingIdentifierSet(ids)
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
