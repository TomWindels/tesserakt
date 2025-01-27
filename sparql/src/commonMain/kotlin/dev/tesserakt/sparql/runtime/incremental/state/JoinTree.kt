package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.core.toMapping
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.delta.*
import dev.tesserakt.sparql.runtime.incremental.state.IncrementalTriplePatternState.Companion.createIncrementalPatternState
import dev.tesserakt.sparql.runtime.incremental.types.Optional
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.incremental.types.Union
import dev.tesserakt.sparql.runtime.util.Bitmask
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * A general join tree type, containing intermediate joined values depending on the tree implementation
 */
internal sealed interface JoinTree: MutableJoinState {

    data object Empty: JoinTree {

        override val bindings: Set<String>
            get() = emptySet()

        override fun peek(delta: DataDelta): List<MappingDelta> {
            return emptyList()
        }

        override fun process(delta: DataDelta) {
            // nothing to do
        }

        override fun join(delta: MappingDelta): List<MappingDelta> {
            return listOf(delta)
        }

        override fun toString(): String = "Empty join tree"

    }

    /**
     * Non-existent join tree
     */
    @JvmInline
    value class None<J: MutableJoinState>(private val states: List<J>): JoinTree {

        override val bindings: Set<String>
            get() = states.flatMapTo(mutableSetOf()) { it.bindings }

        override fun peek(delta: DataDelta): List<MappingDelta> {
            val deltas = states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta) }
                .expandBindingDeltas()
                .flatMap { (completed, delta) -> delta.flatMap { join(completed, it) } }
            return deltas
        }

        override fun process(delta: DataDelta) {
            states.forEach { it.process(delta) }
        }

        override fun join(delta: MappingDelta): List<MappingDelta> {
            return join(completed = Bitmask.wrap(0, length = states.size), delta)
        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (None)")
            states.forEach { state ->
                appendLine("\t || $state")
            }
        }

        fun join(completed: Bitmask, delta: MappingDelta): List<MappingDelta> {
            if (completed.isOne()) {
                return listOf(delta)
            }
            // as we only need to iterate over the patterns not yet managed, we need to inverse the bitmask
            //  before iterating over it
            return when (delta) {
                is MappingAddition -> {
                    var results: List<MappingDelta> = listOf(delta)
                    completed.inv().forEach { i ->
                        results = results.flatMap { states[i].join(it) }
                        if (results.isEmpty()) {
                            return emptyList()
                        }
                    }
                    results
                }

                is MappingDeletion -> {
                    var results: List<MappingDelta> = listOf(delta)
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

            @JvmName("forOptionals")
            operator fun invoke(query: Query.QueryBody, optionals: List<Optional>) = None(
                states = optionals.map { IncrementalOptionalState(query, it) }
            )

        }

    }

    @JvmInline
    value class Dynamic<J: MutableJoinState> private constructor(private val root: Node<J>): JoinTree {

        sealed interface Node<J: MutableJoinState> {

            val bindings: Set<String>

            /**
             * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in this node, without
             *  actually modifying the node
             */
            fun peek(delta: DataDelta): List<MappingDelta>

            /**
             * Processes the [delta], updating the node accordingly
             */
            fun process(delta: DataDelta)

            /**
             * Returns the result of [join]ing the [delta] with its own internal state
             */
            fun join(delta: MappingDelta): List<MappingDelta>

            /**
             * Returns the result of [join]ing the [deltas] with its own internal state
             */
            fun join(deltas: List<MappingDelta>): List<MappingDelta> = deltas.flatMap { delta -> join(delta) }

            fun debugInformation(): String

            @JvmInline
            value class Leaf<J: MutableJoinState>(val state: J): Node<J> {

                override val bindings: Set<String>
                    get() = state.bindings

                override fun peek(delta: DataDelta): List<MappingDelta> {
                    return state.peek(delta)
                }

                override fun process(delta: DataDelta) {
                    state.process(delta)
                }

                override fun join(delta: MappingDelta): List<MappingDelta> {
                    return state.join(delta)
                }

                override fun debugInformation(): String {
                    return "leaf\n$state"
                }
            }

            class Connected<J: MutableJoinState, L: Node<J>, R: Node<J>>(
                private val left: L,
                private val right: R,
                indexes: List<String>
            ): Node<J> {

                override val bindings = left.bindings + right.bindings

                private val buf = mutableJoinCollection(
                    bindings = indexes.intersect(bindings)
                        .also { check(it.isNotEmpty()) { "Connected node used with no valid indices! This is not allowed!" } }
                )

                override fun peek(delta: DataDelta): List<MappingDelta> {
                    val one = left.peek(delta)
                    val two = right.peek(delta)
                    return right.join(one) + left.join(two) + merge(one, two)
                }

                override fun process(delta: DataDelta) {
                    peek(delta).forEach { diff ->
                        when (diff) {
                            is MappingAddition -> buf.add(diff.value)
                            is MappingDeletion -> buf.remove(diff.value)
                        }
                    }
                    left.process(delta)
                    right.process(delta)
                }

                override fun join(delta: MappingDelta): List<MappingDelta> {
                    return delta.transform { buf.join(it) }
                }

                override fun debugInformation() = buildString {
                    var lines = left.debugInformation().lines()
                    if (lines.size > 2) {
                        repeat(lines.size / 2) {
                            appendLine("   ${lines[it]}")
                        }
                        append(" ┌ ")
                        appendLine(lines[lines.size / 2])
                        (lines.size / 2 + 1 until lines.size - 1).forEach {
                            append(" │ ")
                            appendLine(lines[it])
                        }
                        append("/└ ")
                        appendLine(lines.last())
                    } else {
                        append(" ┌ ")
                        appendLine(lines.first())
                        repeat(lines.size - 2) {
                            append(" │ ")
                            appendLine(lines[it + 1])
                        }
                        append("/└ ")
                        appendLine(lines.last())
                    }
                    appendLine("⨉ cached: $buf")
                    lines = right.debugInformation().lines()
                    if (lines.size > 2) {
                        repeat(lines.size / 2) {
                            appendLine("   ${lines[it]}")
                        }
                        append("\\┌ ")
                        appendLine(lines[lines.size / 2])
                        (lines.size / 2 + 1 until lines.size - 1).forEach {
                            append(" │ ")
                            appendLine(lines[it])
                        }
                        append(" └ ")
                        append(lines.last())
                    } else {
                        append("\\┌ ")
                        appendLine(lines.first())
                        repeat(lines.size - 2) {
                            append(" │ ")
                            appendLine(lines[it + 1])
                        }
                        append(" └ ")
                        append(lines.last())
                    }
                }

            }

            class Disconnected<J: MutableJoinState, L: Node<J>, R: Node<J>>(
                val left: L,
                val right: R
            ): Node<J> {

                override val bindings = left.bindings + right.bindings

                override fun peek(delta: DataDelta): List<MappingDelta> {
                    val one = left.peek(delta)
                    val two = right.peek(delta)
                    return right.join(one) + left.join(two) + merge(one, two)
                }

                override fun process(delta: DataDelta) {
                    left.process(delta)
                    right.process(delta)
                }

                override fun join(delta: MappingDelta): List<MappingDelta> {
                    val leftOverlap = delta.value.bindings.keys.count { it in left.bindings }
                    val rightOverlap = delta.value.bindings.keys.count { it in right.bindings }
                    return if (leftOverlap > rightOverlap) {
                        right.join(left.join(delta))
                    } else {
                        left.join(right.join(delta))
                    }
                }

                override fun debugInformation() = buildString {
                    var lines = left.debugInformation().lines()
                    if (lines.size > 2) {
                        repeat(lines.size / 2) {
                            appendLine("   ${lines[it]}")
                        }
                        append(" ┌ ")
                        appendLine(lines[lines.size / 2])
                        (lines.size / 2 + 1 until lines.size - 1).forEach {
                            append(" │ ")
                            appendLine(lines[it])
                        }
                        append("/└ ")
                        appendLine(lines.last())
                    } else {
                        append(" ┌ ")
                        appendLine(lines.first())
                        repeat(lines.size - 2) {
                            append(" │ ")
                            appendLine(lines[it + 1])
                        }
                        append("/└ ")
                        appendLine(lines.last())
                    }
                    appendLine("⨉ not cached")
                    lines = right.debugInformation().lines()
                    if (lines.size > 2) {
                        repeat(lines.size / 2) {
                            appendLine("   ${lines[it]}")
                        }
                        append("\\┌ ")
                        appendLine(lines[lines.size / 2])
                        (lines.size / 2 + 1 until lines.size - 1).forEach {
                            append(" │ ")
                            appendLine(lines[it])
                        }
                        append(" └ ")
                        append(lines.last())
                    } else {
                        append("\\┌ ")
                        appendLine(lines.first())
                        repeat(lines.size - 2) {
                            append(" │ ")
                            appendLine(lines[it + 1])
                        }
                        append(" └ ")
                        append(lines.last())
                    }
                }

            }

        }

        constructor(states: List<J>): this(build(states))

        override val bindings: Set<String>
            get() = root.bindings

        override fun peek(delta: DataDelta): List<MappingDelta> {
            return root.peek(delta)
        }

        override fun process(delta: DataDelta) {
            root.process(delta)
        }

        override fun join(delta: MappingDelta): List<MappingDelta> {
            return root.join(delta)
        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (Dynamic)")
            appendLine(root.debugInformation().prependIndent("    "))
        }

        companion object {

            @JvmName("forPatterns")
            operator fun invoke(patterns: List<Pattern>): Dynamic<IncrementalTriplePatternState<*>> {
                val states = patterns.map { it.createIncrementalPatternState() }
                val root = build(states)
                return Dynamic(root)
            }

            @JvmName("forUnions")
            operator fun invoke(unions: List<Union>): Dynamic<IncrementalUnionState> {
                val states = unions.map { IncrementalUnionState(it) }
                val root = build(states)
                return Dynamic(root)
            }

            @JvmName("forOptionals")
            operator fun invoke(query: Query.QueryBody, optionals: List<Optional>): Dynamic<IncrementalOptionalState> {
                val states = optionals.map { IncrementalOptionalState(query, it) }
                val root = build(states)
                return Dynamic(root)
            }

            /**
             * Builds a tree, returning the tree's root, using the provided [states]
             */
            private fun <J: MutableJoinState> build(states: List<J>): Node<J> {
                check(states.isNotEmpty())
                if (states.size == 1) {
                    // hardly a tree, but what can we do
                    return Node.Leaf(states.single())
                }
                // TODO(perf): actually check individual states on overlapping bindings, have them be connected nodes,
                //  with the total index list depending on the not-yet-inserted patterns
                val remaining = states.mapTo(ArrayList(states.size)) { Node.Leaf(it) }
                var result: Node<J> = Node.Disconnected(left = remaining.removeFirst(), right = remaining.removeFirst())
                while (remaining.isNotEmpty()) {
                    result = Node.Disconnected(left = result, right = remaining.removeFirst())
                }
                return result
            }
        }

    }

    /**
     * A caching strategy only keeping intermediate mapping results cached that form a single chain starting from the
     *  very first element.
     */
    class LeftDeep<J: MutableJoinState>(private val states: List<J>): JoinTree {

        private data class CacheUpdate(
            val addition: Boolean,
            val index: Int,
            val data: Bindings
        )

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

        override val bindings: Set<String> = fallback.bindings

        override fun peek(delta: DataDelta): List<MappingDelta> {
            return states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta) }
                .expandBindingDeltas()
                .flatMap { (completed, solutions) -> join(completed, solutions) }
        }

        override fun process(delta: DataDelta) {
            // first recalculating the delta for the quad, inserting all intermediate results
            val updates = mutableListOf<CacheUpdate>()
            states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta) }
                .expandBindingDeltas()
                // now iterating over every new individual result
                .forEach individualResult@ { (completed, solutions) ->
                    // first inserting the expanded result as is, could be already relevant on its own
                    updates.addAll(process(completed, solutions))
                    // now accelerating using the tree, these new results can then be inserted as-is
                    var (mask, results) = joinUsingTree(completed, solutions)
                    // continuing one-by-one to further extend the tree completely, which the mask should allow
                    //  for based on the `joinUsingTree` already limiting its result to those with 0b...1 variants
                    mask.inv().forEach { i ->
                        if (results.isEmpty()) return@individualResult
                        updates.addAll(process(mask, results))
                        mask = mask.withOnesAt(i)
                        results = results.flatMap { result -> states[i].join(result) }
                    }
                }
            // applying the join tree cache updates
            apply(updates)
            // now also updating the individual states
            states.forEach { it.process(delta) }
        }

        override fun join(delta: MappingDelta): List<MappingDelta> {
            return join(completed = Bitmask.wrap(0, length = states.size), listOf(delta))
        }

        fun join(completed: Bitmask, bindings: List<MappingDelta>): List<MappingDelta> {
            val (mask, results) = joinUsingTree(completed, bindings)
            return results.flatMap { result -> fallback.join(mask, result) }
        }

        fun joinUsingTree(completed: Bitmask, bindings: List<MappingDelta>): Pair<Bitmask, List<MappingDelta>> {
            // checking to see if we're currently matching with an unmatched
            //  result (i.e. from another union, or the empty state check)
            if (completed.isZero()) {
                // TODO(perf) use the last cached type so the fallback structure isn't recreating unnecessary combinations
                return completed to bindings
            }
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

        private fun process(bitmask: Bitmask, bindings: List<MappingDelta>): List<CacheUpdate> {
            // we can't do much here
            if (bindings.isEmpty()) {
                return emptyList()
            }
            // only saving those for which only a > 1 chain of LSBs are set (i.e. accepting 0b011, but not 0b010)
            //  but not those that are completely satisfied (complete solutions) as these can't be joined further
            val satisfied = bitmask.count()
            if (
                satisfied <= 1 ||
                bitmask.size() == satisfied ||
                bitmask.lowestZeroBitIndex() < bitmask.highestOneBitIndex()
            ) {
                return emptyList()
            }
            // shifting the index by one as we don't cache 0b0..1
            val index = bitmask.highestOneBitIndex() - 1
            return bindings.map { binding ->
                CacheUpdate(
                    index = index,
                    addition = binding is MappingAddition,
                    data = binding.value
                )
            }
        }

        private fun apply(updates: List<CacheUpdate>) {
            updates.forEach { update ->
                val cache = cache[update.index]
                when (update.addition) {
                    true -> cache.add(update.data.toMapping())
                    false -> cache.remove(update.data.toMapping())
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

            @JvmName("forOptionals")
            operator fun invoke(query: Query.QueryBody, optionals: List<Optional>) = LeftDeep(
                states = optionals.map { IncrementalOptionalState(query, it) }
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
     * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in child states part of the tree, without
     *  actually modifying the tree
     */
    override fun peek(delta: DataDelta): List<MappingDelta>

    /**
     * Processes the [delta], updating the tree accordingly
     */
    override fun process(delta: DataDelta)

    /**
     * Returns the result of [join]ing the [delta] with its own internal state
     */
    override fun join(delta: MappingDelta): List<MappingDelta>

    /**
     * Returns a string containing debug information (runtime statistics)
     */
    fun debugInformation(): String = " * Join tree statistics unavailable (implementation: ${this::class.simpleName})\n"

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(patterns: List<Pattern>) = when {
            // TODO(perf) specialised empty case
            // TODO(perf) also based on binding overlap
            patterns.size >= 2 -> Dynamic(patterns)
            patterns.isEmpty() -> Empty
            else -> None(patterns)
        }

        @JvmName("forUnions")
        operator fun invoke(unions: List<Union>) = when {
            // TODO(perf) specialised empty case
            // TODO(perf) also based on binding overlap
            unions.size >= 2 -> Dynamic(unions)
            unions.isEmpty() -> Empty
            else -> None(unions)
        }

        @JvmName("forUnions")
        operator fun invoke(parent: Query.QueryBody, optionals: List<Optional>) = when {
            // TODO(perf) specialised empty case
            // TODO(perf) also based on binding overlap
            optionals.size >= 2 -> Dynamic(parent, optionals)
            optionals.isEmpty() -> Empty
            else -> None(parent, optionals)
        }

        @JvmName("forTrees")
        operator fun invoke(trees: List<JoinTree>) = when {
            trees.isEmpty() -> Empty
            trees.size == 1 -> trees.single()
            else -> Dynamic(trees)
        }

        @JvmName("forTrees")
        operator fun invoke(vararg states: MutableJoinState) = when {
            states.isEmpty() -> Empty
            states.size == 1 -> None(listOf(states[0]))
            else -> Dynamic(states.toList())
        }

    }

}
