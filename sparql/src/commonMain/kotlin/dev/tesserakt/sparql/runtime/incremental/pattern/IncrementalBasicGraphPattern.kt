package dev.tesserakt.sparql.runtime.incremental.pattern

import dev.tesserakt.sparql.runtime.core.pattern.TriplePattern
import dev.tesserakt.sparql.runtime.incremental.types.Query

internal data class IncrementalBasicGraphPattern(
    val patterns: List<TriplePattern>
    // TODO: other elements like GROUP BY, BIND, FILTER, ... "modifiers" have to be added here too
) {

    constructor(ast: Query.QueryBody) : this(
        patterns = ast.patterns.map { TriplePattern(it.s, it.p, it.o) }
    )

}
