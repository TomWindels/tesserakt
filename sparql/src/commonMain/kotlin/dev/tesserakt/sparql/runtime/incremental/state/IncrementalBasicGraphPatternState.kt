package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.util.getAllNamedBindings

internal class IncrementalBasicGraphPatternState(ast: Query.QueryBody) {

    private val patterns = JoinTree.None(ast.patterns)
    private val unions = JoinTree.None(ast.unions)

    /**
     * A collection of all bindings found inside this query body; it is not guaranteed that all solutions generated
     *  through [insert]ion have a value for all of these bindings, as this depends on the query itself
     */
    val bindings: Set<String> = ast.getAllNamedBindings().map { it.name }.toSet()

    fun insert(quad: Quad): List<Mapping> {
        val first = patterns.insert(quad)
        val second = unions.insert(quad)
        return patterns.join(second) + unions.join(first)
    }

    fun join(mappings: List<Mapping>): List<Mapping> {
        return patterns.join(mappings)
    }

    fun debugInformation() = buildString {
        appendLine("* Pattern state")
        append(patterns.debugInformation())
    }

}
