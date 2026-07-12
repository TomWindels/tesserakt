package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.IndexedStore
import dev.tesserakt.rdf.types.Quad

internal class IndexedStoreImpl(data: Collection<Quad>) : AbstractStore(), IndexedStore {

    override val context = ImmutableEncodingContextImpl(data)

    private val backing = data
        // making it distinct whilst mapping it to the encoded representation
        .mapTo(mutableSetOf()) {
            // guaranteed to be non-null as the context has been created with the same exact data
            EncodedQuad(context, it)!!
        }
        // list for direct (index) access
        .toList()
    private val subjects = backing.indices.groupBy { i -> backing[i].s }
    private val predicates = backing.indices.groupBy { i -> backing[i].p }
    private val objects = backing.indices.groupBy { i -> backing[i].o }
    private val graphs = backing.indices.groupBy { i -> backing[i].g }

    override val size: Int
        get() = backing.size

    override fun isEmpty(): Boolean {
        return backing.isEmpty()
    }

    override fun encodedIterator(): Iterator<EncodedQuad> {
        return backing.iterator()
    }

    override fun encodedIter(s: Quad.Subject?, p: Quad.Predicate?, o: Quad.Object?, g: Quad.Graph?): Iterator<EncodedQuad> {
        // we can do a little trick: if our filter requires a quad element not known to our encoding context, we know
        //  there are no quads present with such a quad element as any of its fields, so we can simply return the empty
        //  iterator
        val s = s?.let { element ->
            context.encode(element) ?: return emptyIterator()
        }
        val p = p?.let { element ->
            context.encode(element) ?: return emptyIterator()
        }
        val o = o?.let { element ->
            context.encode(element) ?: return emptyIterator()
        }
        val g = g?.let { element ->
            context.encode(element) ?: return emptyIterator()
        }

        if (s == null && p == null && o == null && g == null) {
            return backing.iterator()
        }
        val indices = mutableListOf<List<Int>>()
        if (s != null) {
            indices.add(subjects[s] ?: return emptyIterator())
        }
        if (p != null) {
            indices.add(predicates[p] ?: return emptyIterator())
        }
        if (o != null) {
            indices.add(objects[o] ?: return emptyIterator())
        }
        if (g != null) {
            indices.add(graphs[g] ?: return emptyIterator())
        }
        val iter = quickMerge(indices).iterator()
        return object: Iterator<EncodedQuad> {
            override fun hasNext() = iter.hasNext()
            override fun next() = backing[iter.next()]
        }
    }

}


/**
 * Merges the provided [indices] together to a single index list, only containing items found in all entries.
 *  The size of the result never exceeds the size of the smallest index list passed as argument. The method
 *  assumes all individual index lists to be distinct and sorted in ascending order.
 */
// example: [0, 1, 2] & [2] -> [2]
// TODO: use iterables w/ lazy evaluation instead
private fun quickMerge(indices: List<List<Int>>): List<Int> {
    var result = indices.first()
    var i = indices.size - 1
    while (i > 0) {
        if (result.isEmpty()) {
            return emptyList()
        }
        result = quickMerge(result, indices[i])
        --i
    }
    return result
}

/**
 * Merges the two provided indices [left] and [right] together to a single index list, only containing items
 *  found in both entries. The size of the result never exceeds the size of the smallest index list passed as
 *  an argument. The method assumes the individual index lists to be distinct and sorted in ascending order.
 */
// example: [0, 1, 2] & [2] -> [2]
// TODO: use iterables w/ lazy evaluation instead
private fun quickMerge(left: List<Int>, right: List<Int>): List<Int> {
    var i = 0
    var j = 0
    val result = ArrayList<Int>(minOf(left.size, right.size))
    while (i < left.size && j < right.size) {
        val a = left[i]
        val b = right[j]
        when {
            a == b -> {
                result.add(a)
                ++i
                ++j
            }
            a < b -> {
                ++i
            }
            b < a -> {
                ++j
            }
        }
    }
    return result
}
