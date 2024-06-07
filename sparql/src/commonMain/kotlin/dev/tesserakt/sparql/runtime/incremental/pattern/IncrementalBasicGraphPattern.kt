package dev.tesserakt.sparql.runtime.incremental.pattern

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import dev.tesserakt.sparql.runtime.incremental.types.Query

internal data class IncrementalBasicGraphPattern(
    val patterns: List<TriplePattern>
    // TODO: other elements like GROUP BY, BIND, FILTER, ... "modifiers" have to be added here too
) {

    constructor(ast: Query.QueryBody) : this(
        patterns = ast.patterns.toTriplePatterns()
    )

}

private fun Patterns.toTriplePatterns(): List<TriplePattern> = map {
    when (it.p) {
        is Pattern.NonRepeatingPredicate -> TriplePattern.NonRepeating(
            s = it.s,
            p = it.p,
            o = it.o
        )

        is Pattern.RepeatingPredicate -> TriplePattern.Repeating(
            s = it.s,
            p = it.p.element,
            o = it.o,
            type = it.p.repeatingType()
        )
    }
}

private fun Pattern.RepeatingPredicate.repeatingType() = when (this) {
    is Pattern.OneOrMore -> TriplePattern.Repeating.Type.ONE_OR_MORE

    is Pattern.ZeroOrMore -> TriplePattern.Repeating.Type.ZERO_OR_MORE
}
