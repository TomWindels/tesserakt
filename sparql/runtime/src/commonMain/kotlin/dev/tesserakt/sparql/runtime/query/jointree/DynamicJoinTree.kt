package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.*
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.TriplePattern
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName


@JvmInline
value class DynamicJoinTree private constructor(private val root: Node): JoinTree {

    sealed interface Node {

        val bindings: BindingIdentifierSet

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

        fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

        fun filtered(expression: FilterExpression): Node

        @JvmInline
        value class Leaf(val state: MutableJoinState): Node {

            override val bindings: BindingIdentifierSet
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

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return state.stats(context, granularity)
            }

            override fun filtered(expression: FilterExpression): Node {
                return Leaf(state = state.filtered(expression))
            }

        }

        class Connected(
            internal val left: Node,
            internal val right: Node,
            indexes: BindingIdentifierSet,
            // filters are applied (pushed down) after tree construction, so this is often empty until the tree is
            //  formed
            internal val filters: List<FilterExpression> = emptyList(),
        ): Node {

            override val bindings = left.bindings + right.bindings

            internal val buf = ReindexableMappingArray(indexes)
            private val cache = StreamCache<DataDelta, MappingDelta>()

            override val cardinality: Cardinality
                get() = buf.cardinality

            init {
                check(filters.all { expression -> expression.bindings in bindings })
            }

            override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                return cache.getOrCache(delta) {
                    val one = left.peek(delta)
                    val two = right.peek(delta)
                    right
                        .join(one)
                        .chain(left.join(two))
                        .chain(join(one, two))
                        .filtered { filters.all { expression -> expression.test(it.value) } }
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
                // we don't need to check our filters here: the inner mapping array already filtered out mappings
                //  that do not satisfy our filters (see `peek()`)
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

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                val base = Statistics.JoinedElement(
                    left = left.stats(context, granularity),
                    right = right.stats(context, granularity),
                )
                val inner = if (granularity isAtLeast QueryStatistics.Granularity.DETAILED && filters.isNotEmpty()) {
                    Statistics.DescriptionElement(
                        description = "Filtered\n${filters.joinToString("\n")}",
                        inner = base,
                    )
                } else {
                    base
                }
                return Statistics.SelectiveElement(
                    inner = inner,
                    cardinality = cardinality,
                )
            }

            override fun filtered(expression: FilterExpression): Node {
                // we have to make sure the filter expression fits in at least the combination of both nodes, as
                //  otherwise the expression could not be properly processed within this sub-tree
                check(expression.bindings in this.bindings)
                return when {
                    expression.bindings in left.bindings && expression.bindings in right.bindings -> {
                        // affects both sides, individually, so we don't need to globally filter after the fact
                        Connected(
                            left = left.filtered(expression),
                            right = right.filtered(expression),
                            indexes = buf.indexes,
                            filters = filters,
                        )
                    }
                    expression.bindings in left.bindings && expression.bindings.asIntIterable().none { binding -> binding in right.bindings } -> {
                        // affects the left side only, individually, so we don't need to globally filter after the fact
                        Connected(
                            left = left.filtered(expression),
                            right = right,
                            indexes = buf.indexes,
                            filters = filters,
                        )
                    }
                    expression.bindings.asIntIterable().none { binding -> binding in left.bindings } && expression.bindings in right.bindings -> {
                        // affects the right side only, individually, so we don't need to globally filter after the fact
                        Connected(
                            left = left,
                            right = right.filtered(expression),
                            indexes = buf.indexes,
                            filters = filters,
                        )
                    }
                    else -> {
                        // can only be applied after joining both sides together, so we put the filter as part of this
                        //  node (see check above)
                        Connected(
                            left = left,
                            right = right,
                            indexes = buf.indexes,
                            filters = filters + expression,
                        )
                    }
                }
            }

        }

        class Disconnected(
            internal val left: Node,
            internal val right: Node,
            // filters are applied (pushed down) after tree construction, so this is often empty until the tree is
            //  formed
            internal val filters: List<FilterExpression> = emptyList(),
        ): Node {

            override val bindings = left.bindings + right.bindings

            override val cardinality: Cardinality
                get() = left.cardinality * right.cardinality

            init {
                check(filters.all { expression -> expression.bindings in bindings })
            }

            override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                // peeking in every substate, which will be joined multiple times, so has to be optimised for such
                //  a use
                val one = left.peek(delta).optimisedForReuse()
                val two = right.peek(delta).optimisedForReuse()
                return right
                    .join(one)
                    .chain(left.join(two))
                    .chain(join(one, two))
                    .filtered { filters.all { expression -> expression.test(it.value) } }
                    .optimisedForSingleUse()
            }

            override fun process(delta: DataDelta) {
                left.process(delta)
                right.process(delta)
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                val leftOverlap = delta.value.keys().asIntIterable().count { it in left.bindings }
                val rightOverlap = delta.value.keys().asIntIterable().count { it in right.bindings }
                // as we're joining with our unfiltered nodes, we need to filter out the mappings that do not
                //  adhere to our filters after having joined the two streams
                //  together (so we have all required binding values to evaluate the filter expression(s))
                return if (leftOverlap > rightOverlap) {
                    right.join(left.join(delta).optimisedForSingleUse(left.cardinality))
                        .filtered { filters.all { expression -> expression.test(it.value) } }
                } else {
                    left.join(right.join(delta).optimisedForSingleUse(right.cardinality))
                        .filtered { filters.all { expression -> expression.test(it.value) } }
                }
            }

            override fun reindex(bindings: BindingIdentifierSet) {
                // nothing to do
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                val inner = Statistics.JoinedElement(left = left.stats(context, granularity), right = right.stats(context, granularity))
                return if (granularity isAtLeast QueryStatistics.Granularity.DETAILED && filters.isNotEmpty()) {
                    Statistics.DescriptionElement(
                        description = "Filtered\n${filters.joinToString("\n")}",
                        inner = inner,
                    )
                } else {
                    inner
                }
            }

            override fun filtered(expression: FilterExpression): Node {
                // we have to make sure the filter expression fits in at least the combination of both nodes, as
                //  otherwise the expression could not be properly processed within this sub-tree
                check(expression.bindings in this.bindings)
                return when {
                    expression.bindings in left.bindings && expression.bindings in right.bindings -> {
                        // affects both sides, individually, so we don't need to globally filter after the fact
                        Disconnected(
                            left = left.filtered(expression),
                            right = right.filtered(expression),
                            filters = filters,
                        )
                    }
                    expression.bindings in left.bindings && expression.bindings.asIntIterable().none { binding -> binding in right.bindings } -> {
                        // affects the left side only, individually, so we don't need to globally filter after the fact
                        Disconnected(
                            left = left.filtered(expression),
                            right = right,
                            filters = filters,
                        )
                    }
                    expression.bindings.asIntIterable().none { binding -> binding in left.bindings } && expression.bindings in right.bindings -> {
                        // affects the right side only, individually, so we don't need to globally filter after the fact
                        Disconnected(
                            left = left,
                            right = right.filtered(expression),
                            filters = filters,
                        )
                    }
                    else -> {
                        // can only be applied after joining both sides together, so we put the filter as part of this
                        //  node (see check above)
                        Disconnected(
                            left = left,
                            right = right,
                            filters = filters + expression,
                        )
                    }
                }
            }
        }

    }

    override val bindings: BindingIdentifierSet
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
            is Node.Connected -> {
                root.reindex(bindings, hint)
                // TODO: consider transforming this into a disconnected node if the requested bindings
                //  is empty and both child nodes have no overlap
            }
            is Node.Disconnected -> {
                // nothing to do, as joins are not hashed anyway
                // TODO: consider transforming this into a connected node if the requested bindings
                //  is not empty
            }
            is Node.Leaf -> root.reindex(bindings)
        }
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        return root.stats(context, granularity)
    }

    override fun filtered(filter: FilterExpression): MutableJoinState {
        return DynamicJoinTree(
            root = root.filtered(filter)
        )
    }

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(
            context: QueryContext,
            patterns: List<TriplePattern>,
            filters: List<FilterExpression>,
        ): DynamicJoinTree {
            val states = patterns.map { TriplePatternState.from(context, it) }
            val root = filters.fold(
                initial = build(
                    states = states,
                    prioritizedBindings = filters
                        .fold(BindingIdentifierSet.EMPTY) { set, filter -> set + filter.bindings }
                )
            ) { tree, filter ->
                tree.filtered(filter)
            }
            return DynamicJoinTree(root)
        }

        @JvmName("forPatternStates")
        operator fun invoke(
            patterns: List<TriplePatternState<*>>,
            filters: List<FilterExpression>,
        ): DynamicJoinTree {
            val root = filters.fold(
                initial = build(
                    states = patterns,
                    prioritizedBindings = filters
                        .fold(BindingIdentifierSet.EMPTY) { set, filter -> set + filter.bindings }
                )
            ) { tree, filter ->
                tree.filtered(filter)
            }
            return DynamicJoinTree(root)
        }

        @JvmName("forUnions")
        operator fun invoke(
            context: QueryContext,
            unions: List<Union>,
            filters: List<FilterExpression>,
        ): DynamicJoinTree {
            val states = unions.map { union ->
                UnionState(
                    context = context,
                    union = union,
                    // we don't propagate any of the filters to the unions directly; we use the pushdown managed by the
                    //  nodes instead
                    filters = emptyList()
                )
            }
            val root = filters.fold(
                initial = build(
                    states = states,
                    prioritizedBindings = filters
                        .fold(BindingIdentifierSet.EMPTY) { set, filter -> set + filter.bindings }
                )
            ) { tree, filter ->
                tree.filtered(filter)
            }
            return DynamicJoinTree(root)
        }

        /**
         * Builds a tree, returning the tree's root, using the provided [states]
         */
        private fun build(states: List<MutableJoinState>, prioritizedBindings: BindingIdentifierSet): Node {
            check(states.isNotEmpty())
            if (states.size == 1) {
                // hardly a tree, but what can we do
                return Node.Leaf(states.single())
            }
            return DynamicJoinTreeBuilder.build(states, prioritizedBindings)
        }
    }

}
