package dev.tesserakt.sparql.runtime.incremental.pattern

import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.incremental.types.Query
import dev.tesserakt.sparql.runtime.incremental.types.Union

internal data class IncrementalBasicGraphPattern(
    val patterns: List<Pattern>,
    val unions: List<Union>
    // TODO: other elements like GROUP BY, BIND, FILTER, ... "modifiers" have to be added here too
) {

    constructor(ast: Query.QueryBody) : this(
        patterns = ast.patterns,
        unions = ast.unions
    )

}
