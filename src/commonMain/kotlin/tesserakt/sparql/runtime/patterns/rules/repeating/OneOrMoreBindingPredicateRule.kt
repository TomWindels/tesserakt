package tesserakt.sparql.runtime.patterns.rules.repeating

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.sparql.runtime.types.PatternASTr

internal class OneOrMoreBindingPredicateRule(
    s: PatternASTr.Binding,
    p: PatternASTr.Binding,
    o: PatternASTr.Binding,
) : BindingPredicateRule(s = s, p = p, o = o) {

    override fun expand(input: List<Bindings>, data: MutableMap<Triple.Term, Connections>): List<Bindings> {
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
                    }
                    end != null -> {
                        connections.getAllPathsEndingAt(end)
                            .map { it.asBindings() + bindings }
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
                        }
                        end != null -> {
                            connections.getAllPathsEndingAt(end)
                                .map { it.asBindings() + bindings + (p.name to predicate) }
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

}
