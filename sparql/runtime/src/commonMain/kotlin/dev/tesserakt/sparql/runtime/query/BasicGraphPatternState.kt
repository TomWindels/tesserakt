package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.optimisedForSingleUse
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.getAllNamedBindings

class BasicGraphPatternState private constructor(
    val context: QueryContext,
    private val group: MutableJoinState,
    private val filters: GraphPatternFilterState,
    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    override val bindings: BindingIdentifierSet,
): MutableJoinState {

    // we don't check the cardinality after filtering, as doing so would be expensive
    override val cardinality: Cardinality
        get() = group.cardinality

    override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
        // getting the max amount of mappings we can yield based on the inner group
        return filters.peek(group, delta).optimisedForSingleUse()
    }

    override fun process(delta: DataDelta) {
        group.process(delta)
        filters.process(delta)
    }

    override fun join(delta: MappingDelta): Stream<MappingDelta> {
        return filters.filter(group.join(delta))
    }

    override fun reindex(
        bindings: BindingIdentifierSet,
        hint: MappingArrayHint
    ) {
        group.reindex(bindings, hint)
    }

    override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        val base = group.stats(context, granularity)
        return filters.stats(context, base, granularity)
    }

    companion object {

        operator fun invoke(
            context: QueryContext,
            ast: GraphPattern,
            /**
             * In case this is a state that originates from an inner query structure (e.g. part of a union), filters
             *  from outer scopes can be passed here for push down purposes
             */
            externalFilters: List<FilterExpression>
        ): MutableJoinState {
            val filters = ast.filters.mapNotNull { filter ->
                val expression = (filter as? Filter.Predicate)?.expression ?: return@mapNotNull null
                FilterExpression(context, expression)
            } + externalFilters
            val group = GroupPatternState(
                context = context,
                pattern = ast.patterns,
                unions = ast.unions,
                filters = filters,
            )
            val inner = BasicGraphPatternState(
                context = context,
                group = group,
                filters = GraphPatternFilterState(context, parent = group, filters = ast.filters),
                bindings = BindingIdentifierSet(
                    context = context,
                    names = ast.patterns.getAllNamedBindings().map { it.name }).plus(
                        BindingIdentifierSet(
                            context = context,
                            names = ast.unions.getAllNamedBindings().map { it.name })
                    ),
            )
            if (ast.optional.isEmpty()) {
                return inner
            }
            return OptionalState(
                context = context,
                inner = inner,
                optionals = ast.optional,
                filters = filters,
            )
        }

    }

}
