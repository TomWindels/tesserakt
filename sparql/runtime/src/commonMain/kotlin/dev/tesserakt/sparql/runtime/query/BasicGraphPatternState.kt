package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.QueryContext
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.collect
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.getAllNamedBindings

class BasicGraphPatternState(val context: QueryContext, ast: GraphPattern) {

    private val group = GroupPatternState(context, ast.patterns, ast.unions)

    private val filters = GraphPatternFilterState(context, parent = group, filters = ast.filters)

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String> = ast.getAllNamedBindings().map { it.name }.toSet()

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

    fun debugInformation() = buildString {
        append(group.debugInformation())
        append(filters.debugInformation())
    }

}
