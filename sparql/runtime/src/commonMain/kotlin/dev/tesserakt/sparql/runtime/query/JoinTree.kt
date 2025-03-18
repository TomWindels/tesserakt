package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.collection.MappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Bitmask
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.OneCardinality
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * A general join tree type, containing intermediate joined values depending on the tree implementation
 */
sealed interface JoinTree: MutableJoinState {

    data object Empty: JoinTree {

        override val bindings: Set<String>
            get() = emptySet()

        override val cardinality: Cardinality
            get() = OneCardinality // always matches

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            return emptyStream()
        }

        override fun process(delta: DataDelta) {
            // nothing to do
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return streamOf(delta)
        }

        override fun toString(): String = "Empty join tree"

    }

    /**
     * Non-existent join tree
     */
    // TODO(perf): proper stream use
    @JvmInline
    value class None<J: MutableJoinState>(private val states: List<J>): JoinTree {

        override val bindings: Set<String>
            get() = states.flatMapTo(mutableSetOf()) { it.bindings }

        override val cardinality: Cardinality
            get() = states.fold(OneCardinality) { acc, state -> acc * state.cardinality }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            val deltas = states
                .mapIndexed { i, pattern -> Bitmask.onesAt(i, length = states.size) to pattern.peek(delta).toList() }
                .expandBindingDeltas()
                .flatMap { (completed, delta) -> delta.flatMap { join(completed, it) } }
            return deltas.toStream()
        }

        override fun process(delta: DataDelta) {
            states.forEach { it.process(delta) }
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return join(completed = Bitmask.wrap(0, length = states.size), delta).toStream()
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
            operator fun invoke(patterns: List<TriplePattern>) = None(
                states = patterns.map { TriplePatternState.from(it) }
            )

            @JvmName("forUnions")
            operator fun invoke(unions: List<Union>) = None(
                states = unions.map { UnionState(it) }
            )

        }

    }

    @JvmInline
    value class Dynamic<J: MutableJoinState> private constructor(private val root: Node<J>): JoinTree {

        sealed interface Node<J: MutableJoinState> {

            val bindings: Set<String>

            val cardinality: Cardinality

            /**
             * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in this node, without
             *  actually modifying the node
             */
            fun peek(delta: DataDelta): OptimisedStream<MappingDelta>

            /**
             * Processes the [delta], updating the node accordingly
             */
            fun process(delta: DataDelta)

            /**
             * Returns the result of [join]ing the [delta] with its own internal state
             */
            fun join(delta: MappingDelta): Stream<MappingDelta>

            /**
             * Returns the result of [join]ing the [deltas] with its own internal state
             */
            fun join(deltas: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
                deltas.transform(maxCardinality = this.cardinality) { delta -> join(delta) }

            fun debugInformation(): String

            @JvmInline
            value class Leaf<J: MutableJoinState>(val state: J): Node<J> {

                override val bindings: Set<String>
                    get() = state.bindings

                override val cardinality: Cardinality
                    get() = state.cardinality

                override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                    return state.peek(delta)
                }

                override fun process(delta: DataDelta) {
                    state.process(delta)
                }

                override fun join(delta: MappingDelta): Stream<MappingDelta> {
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

                private val buf = MappingArray(
                    bindings = indexes.intersect(bindings)
                        .also { check(it.isNotEmpty()) { "Connected node used with no valid indices! This is not allowed!" } }
                )

                override val cardinality: Cardinality
                    get() = buf.cardinality

                override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                    val one = left.peek(delta)
                    val two = right.peek(delta)
                    return right.join(one).chain(left.join(two)).chain(join(one, two)).optimisedForSingleUse()
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

                override fun join(delta: MappingDelta): Stream<MappingDelta> {
                    return delta.mapToStream { buf.join(it) }
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

                override val cardinality: Cardinality
                    get() = left.cardinality * right.cardinality

                override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                    // peeking in every substate, which will be joined multiple times, so has to be optimised for such
                    //  a use
                    val one = left.peek(delta).optimisedForReuse()
                    val two = right.peek(delta).optimisedForReuse()
                    return right.join(one).chain(left.join(two)).chain(join(one, two)).optimisedForSingleUse()
                }

                override fun process(delta: DataDelta) {
                    left.process(delta)
                    right.process(delta)
                }

                override fun join(delta: MappingDelta): Stream<MappingDelta> {
                    val leftOverlap = delta.value.bindings.keys.count { it in left.bindings }
                    val rightOverlap = delta.value.bindings.keys.count { it in right.bindings }
                    return if (leftOverlap > rightOverlap) {
                        right.join(left.join(delta).optimisedForSingleUse(left.cardinality))
                    } else {
                        left.join(right.join(delta).optimisedForSingleUse(right.cardinality))
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

        override val bindings: Set<String>
            get() = root.bindings

        override val cardinality: Cardinality
            get() = root.cardinality

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            return root.peek(delta)
        }

        override fun process(delta: DataDelta) {
            root.process(delta)
        }

        override fun join(delta: MappingDelta): Stream<MappingDelta> {
            return root.join(delta)
        }

        override fun debugInformation() = buildString {
            appendLine(" * Join tree statistics (Dynamic)")
            appendLine(root.debugInformation().prependIndent("    "))
        }

        companion object {

            @JvmName("forPatterns")
            operator fun invoke(patterns: List<TriplePattern>): Dynamic<TriplePatternState<*>> {
                val states = patterns.map { TriplePatternState.from(it) }
                val root = build(states)
                return Dynamic(root)
            }

            @JvmName("forUnions")
            operator fun invoke(unions: List<Union>): Dynamic<UnionState> {
                val states = unions.map { UnionState(it) }
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

    override val bindings: Set<String>

    /**
     * Returns the [MappingDelta] changes that occur when [process]ing the [delta] in child states part of the tree, without
     *  actually modifying the tree
     */
    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta>

    /**
     * Processes the [delta], updating the tree accordingly
     */
    override fun process(delta: DataDelta)

    /**
     * Returns the result of [join]ing the [delta] with its own internal state
     */
    override fun join(delta: MappingDelta): Stream<MappingDelta>

    /**
     * Returns a string containing debug information (runtime statistics)
     */
    fun debugInformation(): String = " * Join tree statistics unavailable (implementation: ${this::class.simpleName})\n"

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(patterns: List<TriplePattern>) = when {
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

    }

}
