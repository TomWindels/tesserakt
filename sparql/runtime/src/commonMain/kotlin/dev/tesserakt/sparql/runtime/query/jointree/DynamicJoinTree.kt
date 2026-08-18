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
                state.reindex(bindings, hint = MappingArrayHint())
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return state.stats(context, granularity)
            }

        }

        class Connected(
            context: QueryContext,
            internal val left: Node,
            internal val right: Node,
            indexes: BindingIdentifierSet,
            internal val filters: List<FilterExpression>,
        ): Node {

            override val bindings = left.bindings + right.bindings

//            internal val buf = ReindexableMappingArray(
//                bindings = indexes,
//                hint = mappingArrayHint {
//                    fixedShape = 0
//                    bindings.asIntIterable().forEach { id ->
//                        fixedShape = fixedShape or (1 shl id)
//                    }
//                }
//            )
            internal val buf = ReindexableMappingArray(indexes)
            private val cache = StreamCache<DataDelta, MappingDelta>()

            override val cardinality: Cardinality
                get() = buf.cardinality

            init {
                check(filters.all { expression -> expression.bindings in bindings })
                // we process our initial state as that of the combination of left and right nodes, as these
                //  can already contain initial data
                val initialData = right
                    .join(left.join(MappingAddition(context.emptyMapping(), null)).optimisedForSingleUse(left.cardinality))
                    .filtered { filters.all { expression -> expression.test(it.value) } }
                initialData.forEach { delta ->
                    check(delta is MappingAddition) { "Got an unexpected mapping deletion event!" }
                    buf.add(delta.value)
                }
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
                val inner = if (granularity isAtLeast QueryStatistics.Granularity.HIGH_LEVEL && filters.isNotEmpty()) {
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

        }

        class Disconnected(
            internal val left: Node,
            internal val right: Node,
            // we don't support filters here; if a filter expression is applied on the result of both our nodes,
            //  we transform ourselves into a connected node, so that filter evaluation is limited
        ): Node {

            override val bindings = left.bindings + right.bindings

            override val cardinality: Cardinality
                get() = left.cardinality * right.cardinality

            override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
                // peeking in every substate, which will be joined multiple times, so has to be optimised for such
                //  a use
                val one = left.peek(delta).optimisedForReuse()
                val two = right.peek(delta).optimisedForReuse()
                return right
                    .join(one)
                    .chain(left.join(two))
                    .chain(join(one, two))
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
                } else {
                    left.join(right.join(delta).optimisedForSingleUse(right.cardinality))
                }
            }

            override fun reindex(bindings: BindingIdentifierSet) {
                // nothing to do
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return Statistics.JoinedElement(left = left.stats(context, granularity), right = right.stats(context, granularity))
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

    companion object {

        @JvmName("forPatterns")
        operator fun invoke(
            context: QueryContext,
            patterns: List<TriplePattern>,
            filters: List<FilterExpression>,
        ): DynamicJoinTree {
            val states = patterns.map { TriplePatternState.from(context, it) }
            return invoke(states, filters)
        }

        @JvmName("forPatternStates")
        operator fun invoke(
            patterns: List<TriplePatternState<*>>,
            filters: List<FilterExpression>,
        ): DynamicJoinTree {
            // we're currently dealing with fresh triple pattern states that need to be prefilled
            // however, before we do that, we need to apply all their relevant filters
            val patterns = if (filters.isEmpty()) {
                // small optimization; if there aren't any filters we need to apply, we don't need to
                //  create a mapped view of the triple pattern states either
                patterns
            } else {
                // we replace all pattern states with filtered variants, so that prefilling them already
                //  has their filter constraints satisfied
                patterns.map { pattern ->
                    filters.fold(pattern) { pattern, filter ->
                        if (filter.bindings in pattern.bindings) {
                            pattern.filtered(filter)
                        } else {
                            pattern
                        }
                    }
                }
            }
            // now it is safe to do all necessary prefilling
            patterns.forEach { it.prefill() }
            // we supply downstream with all filter information; not all have to be applied on a connected node level
            //  however, this depends on the bindings of the individual join tree sections
            val root = build(
                context = patterns[0].context,
                states = patterns,
                filters = filters,
            )
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
            return DynamicJoinTree(
                root = build(
                    context = context,
                    states = states,
                    filters = filters,
                )
            )
        }

        /**
         * Builds a tree, returning the tree's root, using the provided [states]
         */
        private fun build(
            context: QueryContext,
            states: List<MutableJoinState>,
            filters: List<FilterExpression>,
        ): Node {
            check(states.isNotEmpty())
            if (states.size == 1) {
                // hardly a tree, but what can we do
                return Node.Leaf(states.single())
            }
            return DynamicJoinTreeBuilder.build(context, states, filters)
        }
    }

}
