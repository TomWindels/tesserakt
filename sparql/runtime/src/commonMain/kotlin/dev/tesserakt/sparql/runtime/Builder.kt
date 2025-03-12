package dev.tesserakt.sparql.runtime

import dev.tesserakt.sparql.runtime.query.QueryState
import dev.tesserakt.sparql.runtime.query.SelectQueryState
import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.SelectQueryStructure

fun QueryStructure.createState(): QueryState<*, *> = when (this) {
    is SelectQueryStructure -> SelectQueryState(this)
}
