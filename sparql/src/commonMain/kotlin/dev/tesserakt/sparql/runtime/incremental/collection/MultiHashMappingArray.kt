package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.toMapping
import dev.tesserakt.sparql.runtime.incremental.stream.Stream
import dev.tesserakt.sparql.runtime.incremental.stream.emptyIterable

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
internal class MultiHashMappingArray(bindings: Set<String>): MappingArray {

    // the backing structure, contains all mappings ever received
    private val backing = mutableListOf<Mapping?>()
    // the number of holes in the backing structure, indicative of the
    private var holes = 0
    // the underlying hash table, mapping the binding name to the map that indexes it on its value, followed by a list
    //  of indices that should be used in the array above - reason for the use of indices is so evaluating multiple
    //  binding constraints only relies on comparing numbers to compare & merge the total resulting set
    // --
    // the positional indices (`ArrayList<Int>`) is guaranteed to be sorted: see the various insertion implementations;
    //  this further allows efficient lookup of multiple constraints at the same time
    private val index = buildMap<String, MutableMap<Quad.Term, ArrayList<Int>>>(capacity = bindings.size) {
        bindings.forEach { binding ->
            put(binding, mutableMapOf())
        }
    }

    init {
        check(bindings.isNotEmpty()) { "Invalid use of hash join array! No bindings are used!" }
    }

    override val mappings: List<Mapping> get() = if (holes == 0) {
        // should be valid if we're tracking the holes correctly
        @Suppress("UNCHECKED_CAST")
        backing as List<Mapping>
    } else {
        backing
            .also { println("WARN: inefficient mapping retrieval occurred!") }
            .filterNotNullTo(ArrayList(backing.size - holes))
    }

    override val cardinality: Int
        get() = backing.size - holes

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [size] results, or a size of 0 guarantees no results will get generated)
     */
    val size: Int get() = backing.size

    override fun iter(mapping: Mapping): Stream<Mapping> {
        return indexStreamFor(mapping).toStream()
    }

    override fun iter(mappings: List<Mapping>): List<Stream<Mapping>> {
        return indexStreamFor(mappings).map { it.toStream() }
    }

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        val pos = backing.size
        backing.add(mapping)
        index.forEach { index ->
            val value = mapping[index.key]
                ?: throw IllegalArgumentException("Mapping $mapping has no value required for index `${index.key}`")
            index.value
                .getOrPut(value) { arrayListOf() }
                .add(pos)
        }
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Iterable<Mapping>) {
        val iter = mappings.iterator()
        if (!iter.hasNext()) {
            return
        }
        val start = backing.size
        var i = 0
        while (iter.hasNext()) {
            val mapping = iter.next()
            backing.add(mapping)
            index.forEach { index ->
                val value = mapping[index.key]
                    ?: throw IllegalArgumentException("Mapping $mapping has no value required for index `${index.key}`")
                index.value
                    .getOrPut(value) { arrayListOf() }
                    .add(start + i)
            }
            ++i
        }
    }

    override fun remove(mapping: Mapping) {
        holes += 1
        val pos = find(mapping)
        require(pos != -1) { "$mapping cannot be removed from HashJoinArray - not found!" }
        backing[pos] = null
        // considering optimising
        if (shouldOptimise()) {
            optimise()
        }
    }

    override fun removeAll(mappings: Iterable<Mapping>) {
        var count = 0
        mappings.forEach { mapping ->
            val pos = find(mapping)
            require(pos != -1) { "$mapping cannot be removed from HashJoinArray - not found!" }
            backing[pos] = null
            ++count
        }
        holes += count
        // considering optimising
        if (shouldOptimise()) {
            optimise()
        }
    }

    /**
     * Defragments the backing array, setting the hole count to zero, rehashing the existing index accordingly.
     */
    fun optimise() {
        // ignoring optimise requests when there aren't any holes to optimise
        if (holes == 0) {
            return
        }
        defragment()
        rehash()
    }

    private fun shouldOptimise(): Boolean {
        // if more than a third of our capacity are holes, or the total hole count exceeds 100, it's probably a good
        //  idea to optimise
        return holes > backing.size / 3 || holes > 100
    }

    /**
     * Removes the holes from the backing structure, making it possible for the existing capacity to be reused for
     *  new data.
     *
     * IMPORTANT: this should **never** be called without rehashing, as it breaks item positions!
     */
    private fun defragment() {
        // quick fixing the backing array by swapping holes with the final element
        var i = 0
        while (i < size - 1) {
            if (backing[i] == null) {
                backing[i] = backing.removeLast()
            } else {
                ++i
            }
        }
        // trimming the end
        if (backing.isNotEmpty() && backing.last() == null) {
            backing.removeLast()
        }
        // resetting the hole count
        holes = 0
    }

    /**
     * Recreates the hashes based on the values currently present in the backing structure.
     *
     * IMPORTANT: this should not be called when the backing structure contains holes!
     */
    private fun rehash() {
        require(holes == 0) { "Invalid use of the rehashing function!" }
        // 1: removing existing indices
        index.forEach { it.value.forEach { it.value.clear() } }
        // 2: filling it with the new values; we can already ensure it's non-null here, so doing it here to avoid
        //  the additional check
        @Suppress("UNCHECKED_CAST")
        (backing as List<Mapping>).forEachIndexed { i, mapping ->
            index.forEach { index ->
                val value = mapping[index.key]
                    ?: throw IllegalArgumentException("Mapping $mapping has no value required for index `${index.key}`")
                index.value
                    .getOrPut(value) { arrayListOf() }
                    .add(i)
            }
        }
        // 3: clearing the empty index values completely
        index.forEach { it.value.entries.retainAll { it.value.isNotEmpty() } }
    }

    /**
     * Finds the index associated with the [mapping] inside of the [backing] array, or `-1` if it hasn't been found
     */
    private fun find(mapping: Mapping): Int {
        val stream = indexStreamFor(mapping)
        stream.indexes.forEach { i ->
            if (backing[i] == mapping) {
                return i
            }
        }
        return -1
    }

    override fun toString(): String =
        "HashJoinArray (cardinality ${backing.size - holes}, indexed on ${index.keys.joinToString()})"

    /**
     * Returns an iterable set of indices compatible with the provided mapping
     */
    private fun indexStreamFor(reference: Mapping): IndexStream {
        val constraints = reference.filter { it.key in index }
        // if there aren't any constraints, all mappings (the entire backing array) can be returned instead
        if (constraints.isEmpty()) {
            return IndexStream(indexes = backing.indices, cardinality = backing.size - holes)
        }
        // getting all relevant indexes - if any of the mapping's values don't have an ID list present for the reference's
        //  value, we can bail early: none match the reference
        val indexes = constraints
            .map { binding -> index[binding.key]!![binding.value] ?: return IndexStream.NONE }
        // the resulting array cannot be longer than the smallest index found, so if any of them are empty, no results
        //  are found
        val cardinality = indexes.minOf { it.size }
        if (cardinality == 0) {
            return IndexStream.NONE
        }
        // these index arrays are guaranteed to be sorted already (see other notes), so "quickMerge"ing them and mapping
        //  these indexes to their actual value
        val merged = quickMerge(indexes)
        return IndexStream(
            indexes = merged,
            // TODO(perf): when the quickMerge method is updated to be stream-like, this `.size` cardinality estimate
            //  has to be updated to the [cardinality] one instead, which is guaranteed to be
            //  smallest estimate >= merged.size
            cardinality = merged.size
        )
    }

    /**
     * Returns a list of all compatible mappings using the provided reference mappings. References representing a
     *  non-existent binding (`null`) are automatically associated with an empty list
     */
    private fun indexStreamFor(references: List<Mapping?>): List<IndexStream> {
        // separating the individual references into their constraints
        val constraints: Map<Mapping?, List<Int>> = references.indices.groupBy { i ->
            val current = references[i] ?: return@groupBy null
            val constraints = current.keys.filter { it in index }.toSet()
            current.filter { it.key in constraints }.toMapping()
        }
        // with all relevant & unique constraints formed, the compatible mappings w/o redundant lookup can be retrieved
        val mapped = constraints.map { (constraints, indexes) -> (constraints?.let { indexStreamFor(constraints) } ?: IndexStream.NONE) to indexes }
        // now the map can be "exploded" again into its original form
        return mapped
            .flatMapTo(ArrayList(references.size)) { entry -> entry.second.map { i -> i to entry.first } }
            .sortedBy { it.first }
            .map { it.second }
    }

    private data class IndexStream(
        val indexes: Iterable<Int>,
        val cardinality: Int,
    ) {
        companion object {
            val NONE = IndexStream(indexes = emptyIterable(), cardinality = 0)
        }
    }

    private inner class Mapper(
        private val indexes: Iterable<Int>,
        override val cardinality: Int,
    ): Stream<Mapping> {

        private inner class Iter(private val iterator: Iterator<Int>): Iterator<Mapping> {

            private var next = getNext()

            override fun hasNext(): Boolean {
                if (next != null) {
                    return true
                }
                next = getNext()
                return next != null
            }

            override fun next(): Mapping {
                val current = next ?: getNext()
                next = null
                return current ?: throw NoSuchElementException()
            }

            private fun getNext(): Mapping? {
                while (iterator.hasNext()) {
                    return backing[iterator.next()] ?: continue
                }
                return null
            }

        }

        // there's no better way here
        private val _isEmpty by lazy { !iterator().hasNext() }

        override fun isEmpty() = _isEmpty

        override fun supportsEfficientIteration(): Boolean {
            // the number of holes in the backing structure are expected to be insignificant
            return true
        }

        override fun iterator(): Iter {
            return Iter(indexes.iterator())
        }

    }

    private fun IndexStream.toStream(): Stream<Mapping> = Mapper(indexes, cardinality)

    companion object {

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

    }

}
