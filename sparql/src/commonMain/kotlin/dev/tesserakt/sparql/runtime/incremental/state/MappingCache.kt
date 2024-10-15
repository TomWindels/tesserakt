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

    }

    /**
     * A caching strategy only keeping intermediate mapping results cached that form a single chain starting from the
     *  very first element.
     */
    class SingleChainLeft: MappingCache() {

        private val cache = mutableMapOf<Bitmask, MutableList<Mapping>>()

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
            cache.getOrPut(bitmask) { mutableListOf() }.addAll(mappings)
        }

    }

    /**
     * Processes the provided [mappings] associated with the satisfied [bitmask] for plan-based optimal caching
     */
    abstract fun insert(bitmask: Bitmask, mappings: List<Mapping>)

}
