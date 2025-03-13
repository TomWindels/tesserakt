package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.types.Filter
import dev.tesserakt.sparql.types.GraphPattern
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.getAllNamedBindings

class BasicGraphPatternState(ast: GraphPattern) {

    private val group = GroupPatternState(ast.patterns, ast.unions)

    private val excluded = ast.filters
        .filterIsInstance<Filter.NotExists>()
        .map { filter -> NegativeGraphState(parent = group, filter = filter) }
        .toStream()

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
        val filtered = excluded.folded(group.peek(delta)) { results, filter -> filter.filter(results, delta = delta) }
        // FIXME individual results generated here still have to be filtered by the other filters
        val other = excluded.merge { it.peek(delta).transform(group.cardinality) { group.join(it) } }
        return filtered.chain(other)
    }

    fun process(delta: DataDelta) {
        group.process(delta)
        excluded.forEach { it.process(delta) }
    }

    fun join(delta: MappingDelta): Stream<MappingDelta> {
        return excluded.folded(group.join(delta)) { results, excluded -> excluded.filter(results) }
    }

    fun debugInformation() = buildString {
        append(group.debugInformation())
        // TODO: filter state
    }

}
