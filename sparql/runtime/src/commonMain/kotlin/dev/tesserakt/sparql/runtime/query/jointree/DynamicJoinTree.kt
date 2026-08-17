package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.collection.ReindexableMappingArray
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality
import kotlin.jvm.JvmInline


@JvmInline
value class DynamicJoinTree private constructor(private val root: Node): JoinTree {

    sealed interface Node {

        val properties: MutableJoinState.Properties

        val cardinality: Cardinality

        /**
         * Processes the [delta], updating the node accordingly, returning its changes
         */
        fun process(delta: DataDelta): OptimisedStream<MappingDelta>

        /**
         * Returns the result of [join]ing the [delta] with its own internal state
         */
        fun join(delta: MappingDelta): Stream<MappingDelta>

        /**
         * Returns the result of [join]ing the [deltas] with its own internal state
         */
        fun join(deltas: OptimisedStream<MappingDelta>): Stream<MappingDelta> =
            deltas.transform { delta -> join(delta) }

        fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint)

        fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

        @JvmInline
        value class Leaf(val state: MutableJoinState): Node {

            override val properties: MutableJoinState.Properties
                get() = state.properties

            override val cardinality: Cardinality
                get() = state.cardinality

            override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
                return state.process(delta)
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

            override val cardinality: Cardinality
                get() = buf.cardinality

            init {
                check(filters.all { expression -> expression.bindings in properties.guaranteed })
                // we process our initial state as that of the combination of left and right nodes, as these
                //  can already contain initial data
                val initialData = right
                    .join(left.join(MappingAddition(Mapping.EMPTY)).optimisedForSingleUse(left.cardinality))
                    .filtered { filters.all { expression -> expression.test(it.value) } }
                initialData.forEach { delta ->
                    check(delta is MappingAddition) { "Got an unexpected mapping deletion event!" }
                    buf.add(delta.value)
                }
            }

            override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
                val changes = right
                    .join(left.process(delta))
                    .chain(left.join(right.process(delta)))
                    .filtered { change -> filters.all { it.test(change.value) } }
                    .collect()
                changes.forEach { change ->
                    when (change) {
                        is MappingAddition -> buf.add(change.value)
                        is MappingDeletion -> buf.remove(change.value)
                    }
                }
                return changes
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                // we don't need to check our filters here: the inner mapping array already filtered out mappings
                //  that do not satisfy our filters (see `process()`)
                return delta.mapToStream { buf.join(it) }
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

            override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
                val changes = right
                    .join(left.process(delta))
                    .chain(left.join(right.process(delta)))
                    .optimisedForSingleUse()
                return changes
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                val leftOverlap = delta.value.bindings.intersectSize(left.properties.maximum)
                val rightOverlap = delta.value.bindings.intersectSize(right.properties.maximum)
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
        states: List<MutableJoinState>,
        filters: List<FilterExpression>,
        externalBindings: BindingIdentifierSet,
    ): this(
        root = if (states.size == 1) {
            Node.Leaf(states.single())
        } else {
            DynamicJoinTreeBuilder.build(
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

    override fun process(delta: DataDelta): OptimisedStream<MappingDelta> {
        return root.process(delta)
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
