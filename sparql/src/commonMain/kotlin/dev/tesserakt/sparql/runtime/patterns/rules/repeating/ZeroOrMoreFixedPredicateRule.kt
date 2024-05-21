package dev.tesserakt.sparql.runtime.patterns.rules.repeating

import dev.tesserakt.sparql.runtime.types.Bindings
import dev.tesserakt.sparql.runtime.types.PatternASTr
import dev.tesserakt.util.addFront

internal class ZeroOrMoreFixedPredicateRule(
    s: PatternASTr.Binding,
    p: PatternASTr.FixedPredicate,
    o: PatternASTr.Binding,
) : FixedPredicateRule(s = s, p = p, o = o) {

    override fun expand(input: List<Bindings>, data: Connections): List<Bindings> {
        return input.flatMap { bindings ->
            val start = bindings[s.name]
            val end = bindings[o.name]
            when {
                start != null && end != null -> {
                    // resulting count no. of paths of the same binding are returned, no additional data required
                    List(data.countAllConnectionsBetween(start, end)) { bindings }
                }
                start != null -> {
                    data.getAllPathsStartingFrom(start)
                        .map { it.asBindings() + bindings }
                        // adding a null-length relation, meaning end == start
                        .addFront(bindings + (o.name to start))
                }
                end != null -> {
                    data.getAllPathsEndingAt(end)
                        .map { it.asBindings() + bindings }
                        // adding a null-length relation, meaning end == start
                        .addFront(bindings + (s.name to end))
                }
                else -> {
                    data.getAllPaths()
                        .map { it.asBindings() + bindings }
                }
            }
        }
    }

    // FIXME: possibly unwanted double firing when this is the only predicate of a pattern, requires investigation of
    //  behaviour from other engines to see what is preferred (see `nodes` test query)
//    override fun insertAndReturnNewPaths(triple: Quad, data: Connections): List<Bindings> =
//        super.insertAndReturnNewPaths(triple = triple, data = data) +
//        // as this is "zero or more", the start and end are already linked to each other
//        Connections.Segment(start = triple.s, end = triple.s).asBindings() +
//        Connections.Segment(start = triple.o, end = triple.o).asBindings()

}
