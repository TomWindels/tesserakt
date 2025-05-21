package dev.tesserakt.rdf.types

class IndexedStore(store: Store) {

    // list for direct (index) access
    private val backing = store.toList()
    private val i_s = backing.indices.groupBy { i -> backing[i].s }
    private val i_p = backing.indices.groupBy { i -> backing[i].p }
    private val i_o = backing.indices.groupBy { i -> backing[i].o }
    // TODO: indexing graphs?

    fun iter(s: Quad.Term? = null, p: Quad.NamedTerm? = null, o: Quad.Term? = null): Iterator<Quad> {
        if (s == null && p == null && o == null) {
            return backing.iterator()
        }
        val indices = mutableListOf<List<Int>>()
        if (s != null) {
            indices.add(i_s[s] ?: return emptyIterator)
        }
        if (p != null) {
            indices.add(i_p[p] ?: return emptyIterator)
        }
        if (o != null) {
            indices.add(i_o[o] ?: return emptyIterator)
        }
        val iter = quickMerge(indices).iterator()
        return object: Iterator<Quad> {
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
// TODO: this could also use iterables
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
// TODO: this could also use iterables
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

private val emptyIterator = emptyList<Quad>().iterator()
