package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.ast.GraphPattern
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.*
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.getAllNamedBindings

internal class BasicGraphPatternState(ast: GraphPattern) {

    private val patterns = JoinTree(ast.patterns)
    private val unions = JoinTree(ast.unions)

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String> = ast.getAllNamedBindings().map { it.name }.toSet()

    val cardinality: Cardinality
        get() = patterns.cardinality * unions.cardinality

    fun insert(delta: DataDelta): List<MappingDelta> {
        // it's important we collect the results before we process the delta
        val total = peek(delta).collect()
        process(delta)
        return total
    }

    fun peek(delta: DataDelta): Stream<MappingDelta> {
        val first = patterns.peek(delta)
        val second = unions.peek(delta)
        return patterns.join(second).chain(unions.join(first))
    }

    fun process(delta: DataDelta) {
        patterns.process(delta)
        unions.process(delta)
    }

    fun join(delta: MappingDelta): Stream<MappingDelta> {
        return unions.join(patterns.join(delta).optimisedForSingleUse())
    }

    fun debugInformation() = buildString {
        appendLine("* Pattern state")
        append(patterns.debugInformation())
    }

}
