package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.delta.Delta
import dev.tesserakt.sparql.runtime.incremental.delta.transform
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import dev.tesserakt.sparql.runtime.incremental.types.Union
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * A general join tree type, containing intermediate joined values depending on the tree implementation
 */
internal sealed interface JoinTree {

    /**
     * Non-existent join tree
     */
    @JvmInline
    value class None<J: MutableJoinState>(private val states: List<J>): JoinTree {

        override fun peek(delta: Delta.Data): List<Delta.Bindings> {
            val deltas = states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta) }
                .expandBindingDeltas()
                .flatMap { (completed, delta) -> delta.flatMap { join(completed, it) } }
            return deltas
        }

        override fun process(delta: Delta.Data) {
            states.forEach { it.process(delta) }
        }

        override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
            return join(completed = Bitmask.wrap(0, length = states.size), delta)
        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (None)")
            states.forEach { state ->
                appendLine("\t || $state")
            }
        }

        fun join(completed: Bitmask, delta: Delta.Bindings): List<Delta.Bindings> {
            if (completed.isOne()) {
                return listOf(delta)
            }
            // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
            //  before iterating over it
            return when (delta) {
                is Delta.BindingsAddition -> {
                    var results: List<Delta.Bindings> = listOf(delta)
                    completed.inv().forEach { i ->
                        results = results.flatMap { states[i].join(it) }
                        if (results.isEmpty()) {
                            return emptyList()
                        }
                    }
                    results
                }

                is Delta.BindingsDeletion -> {
                    var results: List<Delta.Bindings> = listOf(delta)
                    completed.inv().forEach { i ->
                        results = results.flatMap { states[i].join(it) }
                        if (results.isEmpty()) {
                            return emptyList()
                        }
                    }
                    results
                }
            }
        }

        companion object {

            @JvmName("forPatterns")
            operator fun invoke(patterns: List<Pattern>) = None(
                states = patterns.map { it.createIncrementalPatternState() }
            )

            @JvmName("forUnions")
            operator fun invoke(unions: List<Union>) = None(
                states = unions.map { IncrementalUnionState(it) }
            )

        }

    }

    /**
     * A caching strategy only keeping intermediate mapping results cached that form a single chain starting from the
     *  very first element.
     */
    class LeftDeep<J: MutableJoinState>(private val states: List<J>): JoinTree {

        private val cache = buildList(states.size - 2) {
            val available = mutableSetOf<String>()
            available.addAll(states[0].bindings)
            available.addAll(states[1].bindings)
            repeat(states.size - 2) { i ->
                val next = states[i + 2].bindings
                add(mutableJoinCollection(bindings = next.intersect(available)))
                available += next
            }
        }

        // using a fallback "none" join tree type to fill in the gaps after applying the left deep cache
        private val fallback = None(states)

        override fun peek(delta: Delta.Data): List<Delta.Bindings> {
            return states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta) }
                .expandBindingDeltas()
                .flatMap { (completed, solutions) -> join(completed, solutions) }
        }

        override fun process(delta: Delta.Data) {
            // first recalculating the delta for the quad, inserting all intermediate results
            states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta) }
                .expandBindingDeltas()
                // now iterating over every new individual result
                .forEach individualResult@ { (completed, solutions) ->
                    // first inserting the expanded result as is, could be already relevant on its own
                    process(completed, solutions)
                    // now accelerating using the tree, these new results can then be inserted as-is
                    var (mask, results) = joinUsingTree(completed, solutions)
                    // continuing one-by-one to further extend the tree completely, which the mask should allow
                    //  for based on the `joinUsingTree` already limiting its result to those with 0b...1 variants
                    mask.inv().forEach { i ->
                        if (results.isEmpty()) return@individualResult
                        process(mask, results)
                        mask = mask.withOnesAt(i)
                        results = results.flatMap { result -> states[i].join(result) }
                    }
                }
            // now also updating the individual states
            states.forEach { it.process(delta) }
        }

        override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
            return join(completed = Bitmask.wrap(0, length = states.size), listOf(delta))
        }

        fun join(completed: Bitmask, bindings: List<Delta.Bindings>): List<Delta.Bindings> {
            val (mask, results) = joinUsingTree(completed, bindings)
            return results.flatMap { result -> fallback.join(mask, result) }
        }

        fun joinUsingTree(completed: Bitmask, bindings: List<Delta.Bindings>): Pair<Bitmask, List<Delta.Bindings>> {
            // mask 0b0..1 isn't stored, so only applying cache when there's zeroes at 0..1+
            if (completed.lowestOneBitIndex() < 2) {
                return completed to bindings
            }
            // we can join every mapping for which it's bitmask has trailing zeroes (LSB):
            // * the result for a mask 0b0100 can be grown with cached element 0b0011, yielding 0b0111 (unsatisfied)
            //   as new intermediate result
            // * the result for a mask 0b0101 won't be changed, as there's no compatible cache item available
            // getting its compatible cache variant, which is its lowest one bit, minus 2:
            // * the index is shifted by one (see `insert`)
            // * we're interested in the zero before it (0b100 -> 0b011)
            val index = completed.lowestOneBitIndex() - 2
            val cached = cache.getOrNull(index)
                // wasn't cached (no valid combination found thus far, so no results available)
                ?: run {
                    Debug.onJoinTreeMiss()
                    return completed to emptyList()
                }
            val result = bindings.flatMap { binding -> binding.transform { cached.join(it) } }
            Debug.onJoinTreeHit(result.size)
            // forming the new mask this result adheres to, which is
            //  the original mask | ones (index based length)
            val satisfied = Bitmask.wrap((1 shl (index + 2)) - 1, length = states.size)
            val total = completed or satisfied
            return total to result
        }

        private fun process(bitmask: Bitmask, bindings: List<Delta.Bindings>) {
            // we can't do much here
            if (bindings.isEmpty()) {
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
            val cache = cache[index]
            bindings.forEach { binding ->
                when (binding) {
                    is Delta.BindingsAddition -> cache.add(binding.value)
                    is Delta.BindingsDeletion -> TODO()
                }
            }
        }

        companion object {

            private fun sort(patterns: List<Pattern>): Patterns {
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

            @JvmName("forPatterns")
            operator fun invoke(patterns: List<Pattern>) = LeftDeep(
                states = sort(patterns).map { it.createIncrementalPatternState() }
            )

            @JvmName("forUnions")
            operator fun invoke(unions: List<Union>) = LeftDeep(
                states = unions.map { IncrementalUnionState(it) }
            )

        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (LeftDeep)")
            // first line
            appendLine("    ${states.first()}")
            // middle lines
            (1 until states.size - 1).forEach { i ->
                append("   ")
                append("  ".repeat(i))
                append('└')
                append("|| ")
                appendLine(states[i])
                append("   ")
                append("  ".repeat(i))
                append(' ')
                append("|| ")
                appendLine("joined into ${cache[i - 1]}")
            }
            // final line (doesn't have its own state)
            append("   ")
            append("  ".repeat(states.size - 1))
            append('└')
            append(' ')
            appendLine(states.last())
        }

    }

    /**
     * Returns the [Delta.Bindings] changes that occur when [process]ing the [delta] in child states part of the tree, without
     *  actually modifying the tree
     */
    fun peek(delta: Delta.Data): List<Delta.Bindings>

    /**
     * Processes the [delta], updating the tree accordingly
     */
    fun process(delta: Delta.Data)

    /**
     * Returns the result of [join]ing the [delta] with its own internal state
     */
    fun join(delta: Delta.Bindings): List<Delta.Bindings>

    /**
     * Returns a string containing debug information (runtime statistics)
     */
    fun debugInformation(): String = " * Join tree statistics unavailable (implementation: ${this::class.simpleName})"

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(patterns: List<Pattern>) = when {
            // TODO also based on binding overlap
//            patterns.size >= 3 -> LeftDeep(patterns)
            else -> None(patterns)
        }

        @JvmName("forUnions")
        operator fun invoke(unions: List<Union>) = when {
            // TODO also based on binding overlap
//            unions.size >= 3 -> LeftDeep(unions)
            else -> None(unions)
        }

    }

}
