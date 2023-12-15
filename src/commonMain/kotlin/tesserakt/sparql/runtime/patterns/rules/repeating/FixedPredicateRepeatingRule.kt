package tesserakt.sparql.runtime.patterns.rules.repeating

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings

internal abstract class FixedPredicateRepeatingRule(
    // the intended start element
    s: Binding,
    // the repeating element
    private val p: FixedPredicate,
    // the intended final result
    o: Binding
) : RepeatingRule<RepeatingRule.Connections>(s = s, o = o) {

    open fun insertAndReturnNewPaths(triple: Triple, data: Connections): List<Bindings> {
        val segment = process(triple) ?: return emptyList()
        val paths = data.getAllNewPathsAndInsert(segment)
        return paths.map { it.asBindings() }
    }

    fun quickInsert(triple: Triple, data: Connections) {
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
    private fun process(triple: Triple): Connections.Segment? {
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
