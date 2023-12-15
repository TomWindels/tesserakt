package tesserakt.sparql.runtime.patterns.rules.repeating

import tesserakt.sparql.runtime.types.Bindings

internal class OneOrMoreFixedPredicateRepeatingRule(
    // the intended start element
    s: Binding,
    // the repeating element
    p: FixedPredicate,
    // the intended final result
    o: Binding
) : FixedPredicateRepeatingRule(s = s, p = p, o = o) {

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
