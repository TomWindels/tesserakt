package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.util.Bitmask

/**
 * A general mapping cache type, capable of caching a series of mappings with their corresponding "completion" bitmasks
 *  according to internal (cache-specific) rules
 */
sealed class MappingCache {

    /**
     * A naive caching approach, not actually caching any intermediate results
     */
    data object None: MappingCache() {

        override fun insert(bitmask: Bitmask, mappings: List<Mapping>) {
            // nothing to save, we don't cache anything
        }

        // as we don't cache anything, the input becomes the output directly
        override fun List<Pair<Bitmask, List<Mapping>>>.growUsingCache() = this

    }

    /**
     * A caching strategy only keeping intermediate mapping results cached that form a single chain starting from the
     *  very first element.
     */
    class ChainStart: MappingCache() {

        private val cache = mutableListOf<MutableList<Mapping>?>()

        override fun insert(bitmask: Bitmask, mappings: List<Mapping>) {
            // only saving those for which only a > 1 chain of LSBs are set (i.e. accepting 0b011, but not 0b010)
            //  but not those that are completely satisfied (complete solutions) as these can't be joined further
            val satisfied = bitmask.count()
            if (
                satisfied <= 1 ||
                bitmask.size() == satisfied ||
                bitmask.lowestZeroBitIndex() < bitmask.highestOneBitIndex()
            ) {
                return
            }
            // shifting the index by one as we don't cache 0b0..1
            val index = bitmask.highestOneBitIndex() - 1
            while (cache.size < index) {
                cache.add(null)
            }
            if (cache.size == index) {
                cache.add(mutableListOf())
            }
            cache[index]!!.addAll(mappings)
        }

        override fun List<Pair<Bitmask, List<Mapping>>>.growUsingCache(): List<Pair<Bitmask, List<Mapping>>> {
            // we can join every mapping for which it's bitmask has trailing zeroes (LSB):
            // * the result for a mask 0b0100 can be grown with cached element 0b0011, yielding 0b0111 (unsatisfied)
            //   as new intermediate result
            // * the result for a mask 0b0101 won't be changed, as there's no compatible cache item available
            return map { (mask, mappings) ->
                // mask 0b0..1 isn't stored, so only applying cache when there's zeroes at 0..1+
                if (mask.lowestOneBitIndex() < 2) {
                    return@map mask to mappings
                }
                // getting its compatible cache variant, which is its highest one bit, minus 2:
                // * the index is shifted by one (see `insert`)
                // * we're interested in the zero before it (0b100 -> 0b011)
                val index = mask.lowestOneBitIndex() - 2
                val cached = cache.getOrNull(index)
                    // wasn't cached (no valid combination found thus far)
                    ?: return@map mask to mappings
                val result = mergeCompatibleMappings(cached, mappings)
                // forming the new mask this result adheres to, which is
                //  the original mask | ones (index based length)
                val satisfied = Bitmask.wrap((1 shl (index + 2)) - 1, length = mask.length)
                val total = mask or satisfied
                total to result
            }
        }

    }

    /**
     * Processes the provided [mappings] associated with the satisfied [bitmask] for plan-based optimal caching
     */
    abstract fun insert(bitmask: Bitmask, mappings: List<Mapping>)

    /**
     * Uses this cache to increase the number of satisfied mappings as much as possible, changing the number of mappings.
     */
    abstract fun List<Pair<Bitmask, List<Mapping>>>.growUsingCache(): List<Pair<Bitmask, List<Mapping>>>

}
