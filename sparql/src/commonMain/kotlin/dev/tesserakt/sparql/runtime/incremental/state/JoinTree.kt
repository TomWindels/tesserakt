package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.collection.HashJoinArray
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import dev.tesserakt.sparql.runtime.incremental.types.Union
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings
import kotlin.jvm.JvmInline

/**
 * A general join tree type, containing intermediate joined values depending on the tree implementation
 */
internal sealed interface JoinTree {

    /**
     * Non-existent join tree
     */
    @JvmInline
    value class None<J: JoinStateType> private constructor(private val states: List<J>): JoinTree {

        override fun insert(quad: Quad): List<Mapping> {
            val new = states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.insert(quad) }
                .expandResultSet()
                .flatMap { (completed, mappings) -> join(completed, mappings) }
            return new
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            return join(completed = Bitmask.wrap(0, length = states.size), mappings)
        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (None)")
            states.forEachIndexed { i, state ->
                appendLine("\tState element ${i + 1}: $state")
            }
        }

        private fun join(completed: Bitmask, mappings: List<Mapping>): List<Mapping> {
            // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
            //  before iterating over it
            return completed.inv().fold(mappings) { results, i ->
                if (results.isEmpty()) return emptyList() else states[i].join(results)
            }
        }

        companion object {

            operator fun invoke(patterns: Patterns) = None(
                states = patterns.map { it.createIncrementalPatternState() }
            )

            operator fun invoke(unions: List<Union>) = None(
                states = unions.map { IncrementalUnionState(it) }
            )

        }

    }

    /**
     * A caching strategy only keeping intermediate mapping results cached that form a single chain starting from the
     *  very first element.
     */
    class LeftDeep(patterns: Patterns): JoinTree {

        private val patterns = sort(patterns).map { it.createIncrementalPatternState() }

        private val cache = buildList(patterns.size - 2) {
            val bindings = patterns[0].getAllNamedBindings().map { it.name }.toMutableSet()
            repeat(patterns.size - 2) { i ->
                bindings += patterns[i + 1].getAllNamedBindings().map { it.name }
                add(HashJoinArray(bindings = bindings))
            }
        }

        override fun insert(quad: Quad): List<Mapping> {
            TODO("Not yet implemented")
        }

        override fun join(mappings: List<Mapping>): List<Mapping> {
            TODO("Not yet implemented")
        }

        private fun insert(bitmask: Bitmask, mappings: List<Mapping>) {
            // we can't do much here
            if (mappings.isEmpty()) {
                return
            }
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
            cache[index].addAll(mappings)
        }

        private fun List<Pair<Bitmask, List<Mapping>>>.growUsingCache(): List<Pair<Bitmask, List<Mapping>>> {
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
                    ?: run {
                        Debug.onJoinTreeMiss()
                        return@map mask to mappings
                    }
                val result = cached.join(mappings)
                Debug.onJoinTreeHit(result.size)
                // forming the new mask this result adheres to, which is
                //  the original mask | ones (index based length)
                val satisfied = Bitmask.wrap((1 shl (index + 2)) - 1, length = mask.size())
                val total = mask or satisfied
                total to result
            }
        }

        companion object {

            private fun sort(patterns: Patterns): Patterns {
                if (patterns.isEmpty()) {
                    return Patterns(emptyList())
                }
                val bindings = patterns.associateWith { it.getAllNamedBindings().map { it.name } }.toMutableMap()
                // the first pattern part of the results is the one referencing the most common binding, whilst containing
                //  the least amount of other bindings of its own
                val all = bindings.values.flatten().distinct()
                if (all.isEmpty()) {
                    // no bindings in this query, skipping...
                    return Patterns(patterns)
                }
                val maxBindingOccurrence = all
                    .maxOf { binding -> bindings.values.count { binding in it } }
                val mostCommon = all
                    .filter { binding -> bindings.values.count { binding in it } == maxBindingOccurrence }.toSet()
                val first = bindings
                    .maxBy { it.value.count { patternBinding -> patternBinding in mostCommon } - it.value.size }
                val result = mutableListOf(first.key)
                // always incrementing the value of "how explored" a binding is based on the # of other bindings are present
                //  in the newly added pattern instance; 3 being the max amount of bindings in a single pattern instance
                val exploredBias = first.value.associateWith { 3 - first.value.size }.toMutableMap()
                bindings.remove(first.key)
                // with the first pattern inserted, the rest follow based on the order of inserting the least new bindings,
                //  having the most already in common with those already explored, and introducing bindings that are already
                //  most popular
                while (bindings.isNotEmpty()) {
                    // getting the next item that has (ordered by priority)
                    // the most bindings in common with those explored in large amounts
                    // the least # of bindings
                    val (nextPattern, nextBindings) = bindings.maxBy { (_, bindings) ->
                        val relevance = bindings.fold(3f - bindings.size) { a, b -> exploredBias[b]?.times(a) ?: (a / 2f) }
                        relevance
                    }
                    // further adapting the exploration bias using the same logic as the first pattern
                    nextBindings.forEach {
                        exploredBias[it] = exploredBias.getOrElse(it) { 0 } + (3 - nextBindings.size)
                    }
                    bindings.remove(nextPattern)
                    result.add(nextPattern)
                }
                return Patterns(result)
            }

        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (LeftDeep)")
            cache.forEachIndexed { i, cache ->
                appendLine("\tTree element ${i + 1} has cardinality ${cache?.size}")
            }
        }

    }

    /**
     * Returns the new [Mapping]s that are obtained when [insert]ing the [quad] in child states part of the tree
     */
    fun insert(quad: Quad): List<Mapping>

    /**
     * Returns the result of [join]ing the [mappings] with its own internal state
     */
    fun join(mappings: List<Mapping>): List<Mapping>

    /**
     * Returns a string containing debug information (runtime statistics)
     */
    fun debugInformation(): String = " * Join tree statistics unavailable (implementation: ${this::class.simpleName})"

}
