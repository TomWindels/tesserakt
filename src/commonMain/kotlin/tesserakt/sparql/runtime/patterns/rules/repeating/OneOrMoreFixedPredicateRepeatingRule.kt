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
        val variations = data.getAllPaths()
        return input.flatMap { bindings ->
            val start = bindings[s.name]
            val end = bindings[o.name]
            when {
                start != null && end != null -> {
                    // counting the amount of paths lead up to our required start - to - end destination
                    val count = variations
                        .count { s -> s.start == start && s.end == end }
                    // resulting `count` instances of the same binding, no additional data required
                    List(count) { bindings }
                }
                start != null -> {
                    variations
                        .filter { it.start == start }
                        .map { it.asBindings() + bindings }
                }
                end != null -> {
                    variations
                        .filter { it.end == end }
                        .map { it.asBindings() + bindings }
                }
                else -> {
                    variations
                        .map { it.asBindings() + bindings }
                }
            }
        }
    }

}
