package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.sparql.runtime.incremental.delta.DataDelta
import dev.tesserakt.sparql.runtime.incremental.delta.MappingDelta
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings

internal class IncrementalBasicGraphPatternState(ast: Query.QueryBody) {

    private val patterns = JoinTree(ast.patterns)
    private val unions = JoinTree(ast.unions)

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String> = ast.getAllNamedBindings().map { it.name }.toSet()

    fun insert(delta: DataDelta): List<MappingDelta> {
        val total = peek(delta)
        process(delta)
        return total
    }

    fun peek(delta: DataDelta): List<MappingDelta> {
        val first = patterns.peek(delta)
        val second = unions.peek(delta)
        return patterns.join(second) + unions.join(first)
    }

    fun process(delta: DataDelta) {
        patterns.process(delta)
        unions.process(delta)
    }

    fun join(delta: MappingDelta): List<MappingDelta> {
        return unions.join(patterns.join(delta))
    }

    fun debugInformation() = buildString {
        appendLine("* Pattern state")
        append(patterns.debugInformation())
    }

}
