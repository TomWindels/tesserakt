package dev.tesserakt.sparql.runtime.incremental.patterns.rules.repeating

import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.Pattern

internal class OneOrMoreFixedPredicateRule(
    // the intended start element
    s: Pattern.Binding,
    // the repeating element
    p: Pattern.FixedPredicate,
    // the intended final result
    o: Pattern.Binding
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
                }
                end != null -> {
                    data.getAllPathsEndingAt(end)
                        .map { it.asBindings() + bindings }
                }
                else -> {
                    data.getAllPaths()
                        .map { it.asBindings() + bindings }
                }
            }
        }
    }

}
