package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.*
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.GraphPatternSegment
import dev.tesserakt.sparql.types.SelectQuerySegment
import dev.tesserakt.sparql.types.Union
import dev.tesserakt.sparql.util.Cardinality

class UnionState private constructor(private val state: List<Segment>): MutableJoinState {

    private sealed interface Segment {

        class GraphPatternSegmentState private constructor(
            private val origin: Int,
            private val state: MutableJoinState,
        ): Segment {

            constructor(
                origin: Int,
                context: QueryContext,
                parent: GraphPatternSegment,
                externalFilters: List<FilterExpression>,
                externalBindings: BindingIdentifierSet,
            ): this(
                origin = origin,
                state = BasicGraphPatternState(
                    context = context,
                    ast = parent.pattern,
                    externalFilters = externalFilters,
                    externalBindings = externalBindings,
                ),
            )

            override val properties get() = state.properties

            override val cardinality: Cardinality
                get() = state.cardinality

            override fun enqueue(delta: DataDelta) {
                state.enqueue(delta)
            }

            override fun process(): OptimisedStream<MappingDelta> {
                return state.process().mapped { it.map { mapping -> mapping.withOrigin(origin) } }
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                return state.join(delta).mapped { it.map { mapping -> mapping.withOrigin(origin) } }
            }

            override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
                state.reindex(bindings, hint)
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                return state.stats(context, granularity)
            }

        }

        class SubqueryState(context: QueryContext, parent: SelectQuerySegment): Segment {

            override val properties = TODO()

            override val cardinality: Cardinality
                get() = TODO("Not yet implemented")

            override fun enqueue(delta: DataDelta) {
                TODO("Not yet implemented")
            }

            override fun process(): OptimisedStream<MappingDelta> {
                TODO("Not yet implemented")
            }

            override fun join(delta: MappingDelta): Stream<MappingDelta> {
                TODO("Not yet implemented")
            }

            override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
                TODO("Not yet implemented")
            }

            override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
                TODO("Not yet implemented")
            }

        }

        val properties: MutableJoinState.Properties

        val cardinality: Cardinality

        fun enqueue(delta: DataDelta)

        fun process(): OptimisedStream<MappingDelta>

        fun join(delta: MappingDelta): Stream<MappingDelta>

        fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint)

        fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics

    }

    override val properties = if (state.isEmpty()) {
        MutableJoinState.Properties.EMPTY
    } else {
        val initial = state[0].properties
        (1 ..< state.size).fold(initial) { properties, i ->
            val new = state[i].properties
            MutableJoinState.Properties(
                guaranteed = properties.guaranteed.intersect(new.guaranteed),
                maximum = properties.maximum + new.maximum,
            )
        }
    }

    override val cardinality: Cardinality
        get() = Cardinality(state.sumOf { it.cardinality.toDouble() })

    override fun enqueue(delta: DataDelta) {
        state.forEach { it.enqueue(delta) }
    }

    override fun process(): OptimisedStream<MappingDelta> {
        return state.toStream().transform { it.process() }
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return state.toStream().transform { s -> s.join(delta) }
    }

    override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
        state.forEach { it.reindex(bindings, hint) }
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

        operator fun invoke(
            context: QueryContext,
            union: Union,
            filters: List<FilterExpression>,
            externalBindings: BindingIdentifierSet,
        ): UnionState {
            return UnionState(
                // FIXME `id` has to be transformed based on the # of unions
                state = union.mapIndexed { id, segment ->
                    segment.createIncrementalSegmentState(
                        origin = id,
                        context = context,
                        filters = filters,
                        externalBindings = externalBindings,
                    )
                },
            )
        }

        /* helpers */

        private fun dev.tesserakt.sparql.types.Segment.createIncrementalSegmentState(
            origin: Int,
            context: QueryContext,
            filters: List<FilterExpression>,
            externalBindings: BindingIdentifierSet,
        ) = when (this) {
            is SelectQuerySegment -> Segment.SubqueryState(context, this)
            is GraphPatternSegment -> Segment.GraphPatternSegmentState(
                origin = origin,
                context = context,
                parent = this,
                externalFilters = filters,
                externalBindings = externalBindings,
            )
        }
    }

}
