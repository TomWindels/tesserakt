package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.TriplePatternState
import dev.tesserakt.sparql.runtime.query.UnionState
import dev.tesserakt.sparql.runtime.query.join
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName


@JvmInline
value class DynamicJoinTree<J: MutableJoinState> private constructor(private val root: Node<J>): JoinTree {

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

        fun reindex(bindings: BindingIdentifierSet)

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

            override fun reindex(bindings: BindingIdentifierSet) {
                state.reindex(bindings, hint = MappingArrayHint.DEFAULT)
            }

            override fun debugInformation(): String {
                return "leaf\n$state"
            }
        }

        class Connected<J: MutableJoinState>(
            context: QueryContext,
            internal val left: Node<J>,
            internal val right: Node<J>,
            indexes: Collection<String>
        ): Node<J> {

            override val bindings = left.bindings + right.bindings

            internal val buf = ReindexableMappingArray(
                context = context,
                bindings = indexes
            )
            private val cache = StreamCache<DataDelta, MappingDelta>()

            override val cardinality: Cardinality
                get() = buf.cardinality

            override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                return cache.getOrCache(delta) {
                    val one = left.peek(delta)
                    val two = right.peek(delta)
                    right.join(one).chain(left.join(two)).chain(join(one, two))
                }
            }

            override fun process(delta: DataDelta) {
                peek(delta).forEach { diff ->
                    when (diff) {
                        is MappingAddition -> buf.add(diff.value)
                        is MappingDeletion -> buf.remove(diff.value)
                    }
                }
                // with left and right changing, `peek()` can no longer be cached
                cache.clear()
                left.process(delta)
                right.process(delta)
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                return when (val origin = delta.origin) {
                    is DataAddition, null -> delta.mapToStream { buf.join(it) }
                    is DataDeletion -> {
                        delta.mapToStream {
                            buf.iter(it)
                                .remove(peek(origin).mapped { it.value })
                                .join(it)
                        }
                    }
                }
            }

            override fun reindex(bindings: BindingIdentifierSet) {
                buf.reindex(bindings)
            }

            fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
                buf.reindex(bindings, hint)
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

        class Disconnected<J: MutableJoinState>(
            internal val context: QueryContext,
            internal val left: Node<J>,
            internal val right: Node<J>,
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
                val leftOverlap = delta.value.keys(context).count { it in left.bindings }
                val rightOverlap = delta.value.keys(context).count { it in right.bindings }
                return if (leftOverlap > rightOverlap) {
                    right.join(left.join(delta).optimisedForSingleUse(left.cardinality))
                } else {
                    left.join(right.join(delta).optimisedForSingleUse(right.cardinality))
                }
            }

            override fun reindex(bindings: BindingIdentifierSet) {
                // nothing to do
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

    override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
        // this only affects the root node, as that's the one that is joined with directly
        when (val root = root) {
            is Node.Connected<*> -> {
                root.reindex(bindings, hint)
                // TODO: consider transforming this into a disconnected node if the requested bindings
                //  is empty and both child nodes have no overlap
            }
            is Node.Disconnected<*> -> {
                // nothing to do, as joins are not hashed anyway
                // TODO: consider transforming this into a connected node if the requested bindings
                //  is not empty
            }
            is Node.Leaf<*> -> root.reindex(bindings)
        }
    }

    override fun debugInformation() = buildString {
        appendLine(" * Join tree statistics (Dynamic)")
        appendLine(root.debugInformation().prependIndent("    "))
    }

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(context: QueryContext, patterns: List<TriplePattern>): DynamicJoinTree<TriplePatternState<*>> {
            val states = patterns.map { TriplePatternState.from(context, it) }
            val root = build(context, states)
            return DynamicJoinTree(root)
        }

        @JvmName("forUnions")
        operator fun invoke(context: QueryContext, unions: List<Union>): DynamicJoinTree<UnionState> {
            val states = unions.map { UnionState(context, it) }
            val root = build(context, states)
            return DynamicJoinTree(root)
        }

        /**
         * Builds a tree, returning the tree's root, using the provided [states]
         */
        private fun <J: MutableJoinState> build(context: QueryContext, states: List<J>): Node<J> {
            check(states.isNotEmpty())
            if (states.size == 1) {
                // hardly a tree, but what can we do
                return Node.Leaf(states.single())
            }
            return DynamicJoinTreeBuilder.build(context, states)
        }
    }

}
