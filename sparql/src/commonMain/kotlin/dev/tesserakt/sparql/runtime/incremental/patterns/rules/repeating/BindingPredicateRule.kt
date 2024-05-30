package dev.tesserakt.sparql.runtime.incremental.patterns.rules.repeating

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.Pattern

internal abstract class BindingPredicateRule(
    // the intended start element
    s: Pattern.Binding,
    // the repeating element
    protected val p: Pattern.Binding,
    // the intended final result
    o: Pattern.Binding
) : RepeatingRule<MutableMap<Quad.Term, RepeatingRule.Connections>>(s = s, o = o) {

    open fun insertAndReturnNewPaths(triple: Quad, data: MutableMap<Quad.Term, Connections>): List<Bindings> {
        val segment = Connections.Segment(
            start = triple.s,
            end = triple.o
        )
        val paths = data.getOrPut(triple.p) { Connections() }
            .getAllNewPathsAndInsert(segment)
        return paths.map { it.asBindings() }
    }

    fun quickInsert(triple: Quad, data: MutableMap<Quad.Term, Connections>) {
        val segment = Connections.Segment(
            start = triple.s,
            end = triple.o
        )
        // the returned list isn't used by the callee, but is still necessary to generate so `getAllPaths()` functions
        //  properly
        data.getOrPut(triple.p) { Connections() }
            .getAllNewPathsAndInsert(segment)
    }

    override fun newState(): MutableMap<Quad.Term, Connections> = mutableMapOf()

}
