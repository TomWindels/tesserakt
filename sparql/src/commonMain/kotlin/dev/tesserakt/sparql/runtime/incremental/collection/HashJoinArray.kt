package dev.tesserakt.sparql.runtime.incremental.collection

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith

/**
 * An array useful for storing a series of mappings, capable of joining with other mappings using the hash join
 *  algorithm. Hash tables are created for every binding name passed in the constructor.
 */
internal class HashJoinArray(bindings: Set<String>): JoinCollection {

    // the backing structure, contains all mappings ever received
    private val backing = mutableListOf<Mapping>()
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

    override val mappings: List<Mapping> get() = backing

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [size] results, or a size of 0 guarantees no results will get generated)
     */
    val size: Int get() = backing.size

    /**
     * Adds a mapping to the backing array and indexes it accordingly.
     */
    override fun add(mapping: Mapping) {
        val pos = backing.size
        backing.add(mapping)
        index.forEach { index ->
            val value = mapping[index.key]!!
            index.value
                .getOrPut(value) { arrayListOf() }
                .add(pos)
        }
    }

    /**
     * Adds all mappings to the backing array and indexes it accordingly.
     */
    override fun addAll(mappings: Collection<Mapping>) {
        if (mappings.isEmpty()) {
            return
        }
        val start = backing.size
        backing.addAll(mappings)
        mappings.forEachIndexed { i, mapping ->
            index.forEach { index ->
                val value = mapping[index.key]!!
                index.value
                    .getOrPut(value) { arrayListOf() }
                    .add(start + i)
            }
        }
    }

    override fun remove(mapping: Mapping) {
        // TODO(perf): it might be beneficial to simply replace this element `i` with `null`, so only its own indexes
        //  have to be removed; then a dedicated rebalance method can fill multiple slots at once (avoiding an unnecessarily
        //  large backing collection)
        val pos = backing.indexOfLast { it == mapping }
        backing.removeAt(pos)
        // the indexes of this mapping have to be removed
        index.forEach { index ->
            val value = mapping[index.key]!!
            index.value[value]!!.remove(pos)
        }
        // all subsequent indexes have to be updated as its backing element has been moved
        index.forEach { index ->
            index.value.forEach {
                it.value.forEachIndexed { index, value ->
                    if (value > pos) {
                        it.value[index] = value - 1
                    }
                }
            }
        }
    }

    override fun removeAll(mappings: Collection<Mapping>) {
        // TODO(perf): can be improved, see note at single `remove`
        mappings.forEach { remove(it) }
    }

    override fun join(mapping: Mapping): List<Mapping> {
        val compatible = getCompatibleMappings(mapping)
        return compatible.mapNotNull { if (it.compatibleWith(mapping)) it + mapping else null }
    }

    override fun join(mappings: List<Mapping>): List<Mapping> {
        when (mappings.size) {
            0 -> return emptyList()
            1 -> return join(mappings.first())
        }
        val compatible = getCompatibleMappings(mappings)
        return compatible.indices.flatMap { i ->
            compatible[i].mapNotNull { if (it.compatibleWith(mappings[i])) it + mappings[i] else null }
        }
    }

    override fun join(other: JoinCollection): List<Mapping> {
        return if (other is HashJoinArray && other.index.size > index.size) {
            other.join(backing)
        } else {
            join(other.mappings)
        }
    }

    override fun toString(): String =
        "HashJoinArray (cardinality ${mappings.size}, indexed on ${index.keys.joinToString()})"

    /**
     * Returns a list of all compatible mappings using the provided reference mappings.
     */
    private fun getCompatibleMappings(references: List<Mapping>): List<List<Mapping>> {
        // separating the individual references into their constraints
        val constraints: Map<Mapping, List<Int>> = references.indices.groupBy { i ->
            val current = references[i]
            val constraints = current.keys.filter { it in index }.toSet()
            current.filter { it.key in constraints }
        }
        // with all relevant & unique constraints formed, the compatible mappings w/o redundant lookup can be retrieved
        val mapped = constraints.map { (constraints, indexes) -> getCompatibleMappings(constraints) to indexes }
        // now the map can be "exploded" again into its original form
        return mapped
            .flatMapTo(ArrayList(references.size)) { entry -> entry.second.map { i -> i to entry.first } }
            .sortedBy { it.first }
            .map { it.second }
    }

    /**
     * Returns a list of mappings compatible with the provided mapping
     */
    private fun getCompatibleMappings(reference: Mapping): List<Mapping> {
        val constraints = reference.filter { it.key in index }
        // if there aren't any constraints, all mappings (the entire backing array) can be returned instead
        if (constraints.isEmpty()) {
            return backing
        }
        // getting all relevant indexes - if any of the mapping's values don't have an ID list present for the reference's
        //  value, we can bail early: none match the reference
        val indexes = constraints.map { binding -> index[binding.key]!![binding.value] ?: return emptyList() }
        // the resulting array cannot be longer than the smallest index found, so if any of them are empty, no results
        //  are found
        if (indexes.any { it.isEmpty() }) {
            return emptyList()
        }
        // these index arrays are guaranteed to be sorted already (see other notes), so "quickMerge"ing them and mapping
        //  these indexes to their actual value
        return quickMerge(indexes).map { backing[it] }
    }

    companion object {

        /**
         * Merges the provided [indices] together to a single index list, only containing items found in all entries.
         *  The size of the result never exceeds the size of the smallest index list passed as argument. The method
         *  assumes all individual index lists to be distinct and sorted in ascending order.
         */
        // example: [0, 1, 2] & [2] -> [2]
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
