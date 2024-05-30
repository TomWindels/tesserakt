package dev.tesserakt.sparql.runtime.incremental.patterns.rules.repeating

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.Pattern
import dev.tesserakt.util.addFront

internal class ZeroOrMoreBindingPredicateRule(
    s: Pattern.Binding,
    p: Pattern.Binding,
    o: Pattern.Binding,
) : BindingPredicateRule(s = s, p = p, o = o) {

    override fun expand(input: List<Bindings>, data: MutableMap<Quad.Term, Connections>): List<Bindings> {
        return input.flatMap { bindings ->
            val start = bindings[s.name]
            val end = bindings[o.name]
            // if `p.name` is inside the bindings, we use that one
            bindings[p.name]?.let { predicate ->
                val connections = data[predicate] ?: return@flatMap emptyList<Bindings>()
                when {
                    start != null && end != null -> {
                        // resulting count no. of paths of the same binding are returned, no additional data required
                        List(connections.countAllConnectionsBetween(start, end)) { bindings }
                    }
                    start != null -> {
                        connections.getAllPathsStartingFrom(start)
                            .map { it.asBindings() + bindings }
                            // adding a null-length relation, meaning end == start
                            .addFront(bindings + (o.name to start))
                    }
                    end != null -> {
                        connections.getAllPathsEndingAt(end)
                            .map { it.asBindings() + bindings }
                            // adding a null-length relation, meaning end == start
                            .addFront(bindings + (s.name to end))
                    }
                    else -> {
                        connections.getAllPaths()
                            .map { it.asBindings() + bindings }
                    }
                }
            } ?: run {
                // otherwise, we explode into all possible `p.names` based on the all the ones ever received
                data.flatMap { (predicate, connections) ->
                    when {
                        start != null && end != null -> {
                            // resulting count no. of paths of the same binding are returned, no additional data required
                            List(connections.countAllConnectionsBetween(start, end)) { bindings + (p.name to predicate) }
                        }
                        start != null -> {
                            connections.getAllPathsStartingFrom(start)
                                .map { it.asBindings() + bindings + (p.name to predicate) }
                                // adding a null-length relation, meaning end == start
                                .addFront(bindings + mapOf(o.name to start, p.name to predicate))
                        }
                        end != null -> {
                            connections.getAllPathsEndingAt(end)
                                .map { it.asBindings() + bindings + (p.name to predicate) }
                                // adding a null-length relation, meaning end == start
                                .addFront(bindings + mapOf(s.name to end, p.name to predicate))
                        }
                        else -> {
                            connections.getAllPaths()
                                .map { it.asBindings() + bindings + (p.name to predicate) }
                        }
                    }
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
