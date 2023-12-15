package tesserakt.sparql.runtime.patterns.rules.repeating

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings

internal abstract class BindingPredicateRepeatingRule(
    // the intended start element
    s: Binding,
    // the repeating element
    protected val p: Binding,
    // the intended final result
    o: Binding
) : RepeatingRule<MutableMap<Triple.Term, RepeatingRule.Connections>>(s = s, o = o) {

    open fun insertAndReturnNewPaths(triple: Triple, data: MutableMap<Triple.Term, Connections>): List<Bindings> {
        val segment = Connections.Segment(
            start = triple.s,
            end = triple.o
        )
        val paths = data.getOrPut(triple.p) { Connections() }
            .getAllNewPathsAndInsert(segment)
        return paths.map { it.asBindings() }
    }

    fun quickInsert(triple: Triple, data: MutableMap<Triple.Term, Connections>) {
        val segment = Connections.Segment(
            start = triple.s,
            end = triple.o
        )
        // the returned list isn't used by the callee, but is still necessary to generate so `getAllPaths()` functions
        //  properly
        data.getOrPut(triple.p) { Connections() }
            .getAllNewPathsAndInsert(segment)
    }

    override fun newState(): MutableMap<Triple.Term, Connections> = mutableMapOf()

}
