package dev.tesserakt.sparql.runtime

import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.runtime.query.SelectQueryState
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.SelectQueryStructure

fun QueryState(
    structure: QueryStructure,
    context: EncodingContext? = null,
): QueryState<*, *> {
    return when (structure) {
        is SelectQueryStructure -> SelectQueryState(structure, context)
    }
}
