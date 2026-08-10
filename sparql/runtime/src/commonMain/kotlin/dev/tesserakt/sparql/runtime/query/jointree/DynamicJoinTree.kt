package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.join
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline


@JvmInline
value class DynamicJoinTree private constructor(private val root: Node): JoinTree {

    sealed interface Node {

        val properties: MutableJoinState.Properties

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

        fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint)

        fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

        @JvmInline
        value class Leaf(val state: MutableJoinState): Node {

            override val properties: MutableJoinState.Properties
                get() = state.properties

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

            override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
                state.reindex(bindings, hint)
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

            override val properties = MutableJoinState.Properties(
                guaranteed = left.properties.guaranteed + right.properties.guaranteed,
                maximum = left.properties.maximum + right.properties.maximum,
            )

            internal val buf = ReindexableMappingArray(indexes)
            private val cache = StreamCache<DataDelta, MappingDelta>()

            override val cardinality: Cardinality
                get() = buf.cardinality

            init {
                check(filters.all { expression -> expression.bindings in properties.guaranteed })
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

            override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
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

            override val properties = MutableJoinState.Properties(
                guaranteed = left.properties.guaranteed + right.properties.guaranteed,
                maximum = left.properties.maximum + right.properties.maximum,
            )

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
                val leftOverlap = delta.value.keys().asIntIterable().count { it in left.properties.maximum }
                val rightOverlap = delta.value.keys().asIntIterable().count { it in right.properties.maximum }
                // as we're joining with our unfiltered nodes, we need to filter out the mappings that do not
                //  adhere to our filters after having joined the two streams
                //  together (so we have all required binding values to evaluate the filter expression(s))
                return if (leftOverlap > rightOverlap) {
                    right.join(left.join(delta).optimisedForSingleUse(left.cardinality))
                } else {
                    left.join(right.join(delta).optimisedForSingleUse(right.cardinality))
                }
            }

            override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
                // nothing to do
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return Statistics.JoinedElement(left = left.stats(context, granularity), right = right.stats(context, granularity))
            }

        }

    }

    constructor(
        context: QueryContext,
        states: List<MutableJoinState>,
        filters: List<FilterExpression>,
        externalBindings: BindingIdentifierSet,
    ): this(
        root = if (states.size == 1) {
            Node.Leaf(states.single())
        } else {
            DynamicJoinTreeBuilder.build(
                context = context,
                states = states,
                filters = filters,
                externalBindings = externalBindings,
            )
        }
    )

    override val properties: MutableJoinState.Properties
        get() = root.properties

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
            is Node.Leaf -> root.reindex(bindings, hint)
        }
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        return root.stats(context, granularity)
    }

}
