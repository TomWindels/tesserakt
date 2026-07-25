package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.collect
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
    val bindings: BindingIdentifierSet,
) {

    // we don't check the cardinality after filtering, as doing so would be expensive
    val cardinality: Cardinality
        get() = group.cardinality

    fun insert(delta: DataDelta): List<MappingDelta> {
        // it's important we collect the results before we process the delta
        val total = peek(delta).collect()
        process(delta)
        return total
    }

    fun peek(delta: DataDelta): Stream<MappingDelta> {
        // getting the max amount of mappings we can yield based on the inner group
        return filters.peek(group, delta)
    }

    fun process(delta: DataDelta) {
        group.process(delta)
        filters.process(delta)
    }

    fun join(delta: MappingDelta): Stream<MappingDelta> {
        return filters.filter(group.join(delta))
    }

    fun stats(context: QueryContext, granularity: QueryStatistics.Granularity): Statistics {
        val base = group.stats(context, granularity)
        return filters.stats(context, base, granularity)
    }

    /**
     * Returns a copy of self with the [expr] applied to its inner group of triple patterns.
     *
     * IMPORTANT: this expression **is not applied** to stateful
     *  filters (the active [GraphPatternFilterState]), as that would not create the same query results!
     */
    fun filtered(expr: FilterExpression): BasicGraphPatternState {
        return BasicGraphPatternState(
            context = context,
            group = group.filtered(expr),
            filters = filters,
            bindings = bindings,
        )
    }

    companion object {

        operator fun invoke(
            context: QueryContext,
            ast: GraphPattern,
        ): BasicGraphPatternState {
            val group = GroupPatternState(
                context = context,
                pattern = ast.patterns,
                unions = ast.unions,
                filters = ast.filters.mapNotNull { filter ->
                    val expression = (filter as? Filter.Predicate)?.expression ?: return@mapNotNull null
                    FilterExpression(context, expression)
                },
            )
            return BasicGraphPatternState(
                context = context,
                group = group,
                filters = GraphPatternFilterState(context, parent = group, filters = ast.filters),
                bindings = BindingIdentifierSet(context, ast.getAllNamedBindings().map { it.name }),
            )
        }

    }

}
