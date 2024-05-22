package dev.tesserakt.sparql.runtime.incremental.patterns.rules.repeating

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.types.Bindings
import dev.tesserakt.sparql.runtime.types.PatternASTr

internal abstract class FixedPredicateRule(
    // the intended start element
    s: PatternASTr.Binding,
    // the repeating element
    private val p: PatternASTr.FixedPredicate,
    // the intended final result
    o: PatternASTr.Binding
) : RepeatingRule<RepeatingRule.Connections>(s = s, o = o) {

    open fun insertAndReturnNewPaths(triple: Quad, data: Connections): List<Bindings> {
        val segment = process(triple) ?: return emptyList()
        val paths = data.getAllNewPathsAndInsert(segment)
        return paths.map { it.asBindings() }
    }

    fun quickInsert(triple: Quad, data: Connections) {
        val segment = process(triple) ?: return
        // the returned list isn't used by the callee, but is still necessary to generate so `getAllPaths()` functions
        //  properly
        data.getAllNewPathsAndInsert(segment)
    }

    override fun newState(): Connections = Connections()

    /**
     * Processes the incoming triple, returns `null` if no match is found, otherwise a connection object
     *  containing a fixed/bound/open start/end
     */
    private fun process(triple: Quad): Connections.Segment? {
        if (!p.matches(triple.p)) {
            return null
        }
        val entry = Connections.Segment(
            start = triple.s,
            end = triple.o
        )
        return entry
    }

}
