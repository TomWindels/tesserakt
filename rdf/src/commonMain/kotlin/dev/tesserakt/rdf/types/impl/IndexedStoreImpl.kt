package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.*

// same logic as `StoreImpl`
@Suppress("EqualsOrHashCode")
internal class IndexedStoreImpl : AbstractStore, IndexedStore {

    override val context: EncodingContext

    // flattened encoded s, p, o, g pairs, allowing direct access
    private val backing: IntArray

    // the various indices, built on top of the backing list positions
    private val subjects: Map<Int, IntArray>
    private val predicates: Map<Int, IntArray>
    private val objects: Map<Int, IntArray>
    private val graphs: Map<Int, IntArray>

    // considering the contents don't change, we can cache the collection's hash code
    private val hashCode by lazy { super.hashCode() }

    override val size: Int
        // all have identical size
        get() = backing.size shr 2

    constructor(data: Collection<Quad>) {
        // we don't want to risk getting an encoding context that could see terms being removed, as we create an
        //  immutable view of the data
        if (data is Store && data.context !is MutableEncodingContext) {
            this.backing = flatten(data.asEncodedSet())
            this.context = data.context
        } else {
            val quads = HashSet<EncodedQuad>(data.size)
            this.context = ImmutableEncodingContextImpl(data, quads)
            this.backing = flatten(quads)
        }
        this.subjects = createIndex(backing, 0)
        this.predicates = createIndex(backing, 1)
        this.objects = createIndex(backing, 2)
        this.graphs = createIndex(backing, 3)
    }

    override fun isEmpty(): Boolean {
        return backing.isEmpty()
    }

    override fun encodedIterator(): Iterator<EncodedQuad> {
        return object: Iterator<EncodedQuad> {
            private val inner = this@IndexedStoreImpl.indices.iterator()

            override fun hasNext(): Boolean {
                return inner.hasNext()
            }

            override fun next(): EncodedQuad {
                return get(inner.next())
            }
        }
    }

    override fun encodedIter(
        s: EncodedQuadElement,
        p: EncodedQuadElement,
        o: EncodedQuadElement,
        g: EncodedQuadElement
    ): Iterator<EncodedQuad> {
        if (s == Int.MIN_VALUE && p == Int.MIN_VALUE && o == Int.MIN_VALUE && g == Int.MIN_VALUE) {
            return encodedIterator()
        }
        val indices = mutableListOf<IntArray>()
        if (s != Int.MIN_VALUE) {
            indices.add(subjects[s] ?: return emptyIterator())
        }
        if (p != Int.MIN_VALUE) {
            indices.add(predicates[p] ?: return emptyIterator())
        }
        if (o != Int.MIN_VALUE) {
            indices.add(objects[o] ?: return emptyIterator())
        }
        if (g != Int.MIN_VALUE) {
            indices.add(graphs[g] ?: return emptyIterator())
        }
        val iter = quickMerge(indices).iterator()
        return object: Iterator<EncodedQuad> {
            override fun hasNext() = iter.hasNext()
            override fun next() = get(iter.next())
        }
    }

    private fun get(index: Int): EncodedQuad {
        val index = index shl 2
        return EncodedQuad(
            s = this.backing[index],
            p = this.backing[index + 1],
            o = this.backing[index + 2],
            g = this.backing[index + 3],
        )
    }

    override fun hashCode(): Int {
        return hashCode
    }

}


/**
 * Merges the provided [indices] together to a single index list, only containing items found in all entries.
 *  The size of the result never exceeds the size of the smallest index list passed as argument. The method
 *  assumes all individual index lists to be distinct and sorted in ascending order.
 */
// example: [0, 1, 2] & [2] -> [2]
// TODO: use iterables w/ lazy evaluation instead
private fun quickMerge(indices: List<IntArray>): IntArray {
    var result = indices.first()
    var i = indices.size - 1
    while (i > 0) {
        if (result.isEmpty()) {
            return IntArray(0)
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
private fun quickMerge(left: IntArray, right: IntArray): IntArray {
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
    return result.toIntArray()
}

private fun createIndex(backing: IntArray, offset: Int): Map<Int, IntArray> {
    return backing
        .indices
        .step(4)
        .groupBy { i -> backing[i + offset] }
        .mapValues { raw ->
            // the raw indexes point to their absolute position in the backing array;
            //  we want them to point to the start of the actual encoded quad (so i / 4)
            // the offset is then also dropped as a result
            IntArray(raw.value.size) { i -> raw.value[i] shr 2 }
        }
}

private fun flatten(set: Set<EncodedQuad>): IntArray {
    val iter = set.iterator()
    val arr = IntArray(set.size * 4)
    var i = 0
    while (iter.hasNext()) {
        val q = iter.next()
        arr[i++] = q.s
        arr[i++] = q.p
        arr[i++] = q.o
        arr[i++] = q.g
    }
    check(arr.size == i) { "Reported collection size and iterator count mismatch!" }
    return arr
}
