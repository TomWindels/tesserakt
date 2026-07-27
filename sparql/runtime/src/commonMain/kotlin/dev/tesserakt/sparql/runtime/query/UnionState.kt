package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.GraphPatternSegment
import dev.tesserakt.sparql.types.SelectQuerySegment
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality

class UnionState private constructor(private val state: List<Segment>): MutableJoinState {

    private sealed class Segment {

        class GraphPatternSegmentState private constructor(
            private val state: BasicGraphPatternState,
        ): Segment() {

            constructor(
                context: QueryContext,
                parent: GraphPatternSegment,
                externalFilters: List<FilterExpression>
            ): this(
                state = BasicGraphPatternState(context, parent.pattern, externalFilters),
            )

            override val bindings get() = state.bindings

            override val cardinality: Cardinality
                get() = state.cardinality

            override fun peek(delta: DataDelta): Stream<MappingDelta> {
                return state.peek(delta)
            }

            override fun process(delta: DataDelta) {
                return state.process(delta)
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                return state.join(delta)
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return state.stats(context, granularity)
            }

        }

        class SubqueryState(context: QueryContext, parent: SelectQuerySegment): Segment() {

            override val bindings = BindingIdentifierSet(context, parent.query.bindings)

            override val cardinality: Cardinality
                get() = TODO("Not yet implemented")

            override fun peek(delta: DataDelta): Stream<MappingDelta> {
                TODO("Not yet implemented")
            }

            override fun process(delta: DataDelta) {
                TODO("Not yet implemented")
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                TODO("Not yet implemented")
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                TODO("Not yet implemented")
            }

        }

        abstract val bindings: BindingIdentifierSet

        abstract val cardinality: Cardinality

        abstract fun peek(delta: DataDelta): Stream<MappingDelta>

        abstract fun process(delta: DataDelta)

        abstract fun join(delta: MappingDelta): Stream<MappingDelta>

        abstract fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

    }

    constructor(
        context: QueryContext,
        union: Union,
        filters: List<FilterExpression>,
    ): this(
        state = union.map {
            it.createIncrementalSegmentState(context = context, filters = filters)
        },
    )

    override val bindings: BindingIdentifierSet = when {
        state.isEmpty() -> BindingIdentifierSet.EMPTY
        state.size == 1 -> state[0].bindings
        else -> {
            (1 ..< state.size).fold(state[0].bindings) { bindings, i -> bindings + state[i].bindings }
        }
    }

    override val cardinality: Cardinality
        get() = Cardinality(state.sumOf { it.cardinality.toDouble() })

    override fun process(delta: DataDelta) {
        state.forEach { it.process(delta) }
    }

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        // whilst the max cardinality here is not correct in all cases, it covers most bases
        return state.toStream().transform(maxCardinality = 1) { it.peek(delta) }.optimisedForReuse()
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return state.toStream().transform(maxCardinality = state.maxOf { it.cardinality }) { s -> s.join(delta) }
    }

    override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
        // TODO: not yet implemented
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        fun Statistics.describedAsSegment(): Statistics =
            if (granularity isAtLeast QueryStatistics.Granularity.DETAILED) {
                Statistics.DescriptionElement(inner = this, description = "Union segment")
            } else {
                this
            }

        val inner = when (state.size) {
            0 -> return Statistics.Empty
            1 -> {
                state[0].stats(context, granularity).describedAsSegment()
            }
            else -> {
                (1 ..< state.size).fold(state[0].stats(context, granularity).describedAsSegment()) { stats, i ->
                    val state = state[i]
                    Statistics.JoinedElement(
                        left = stats,
                        right = state.stats(context, granularity).describedAsSegment()
                    )
                }
            }
        }
        return if (granularity isAtLeast QueryStatistics.Granularity.DETAILED) {
            Statistics.DescriptionElement(
                inner = inner,
                description = "Union"
            )
        } else {
            inner
        }
    }

    companion object {

        /* helpers */

        private fun dev.tesserakt.sparql.types.Segment.createIncrementalSegmentState(context: QueryContext, filters: List<FilterExpression>) = when (this) {
            is SelectQuerySegment -> Segment.SubqueryState(context, this)
            is GraphPatternSegment -> Segment.GraphPatternSegmentState(context, this, filters)
        }
    }

}
