package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.types.SelectQueryStructure

interface OutputState: Collection<Mapping> {

    fun onResultAdded(result: Mapping)

    fun onResultRemoved(result: Mapping)

    companion object {

        operator fun invoke(context: QueryContext, ast: SelectQueryStructure): OutputState {
            val ordering = ast.ordering
            return when {
                ordering != null -> {
                    UnconfinedOrderedOutputState(comparator = OrderComparator(context, ordering))
                }
                else -> {
                    UnconfinedOutputState()
                }
            }
        }
    }

}
