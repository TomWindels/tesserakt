package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.types.SelectQueryStructure

interface OutputState: Collection<Mapping> {

    /**
     * A subset of all [OutputState] implementations, used as a valid inner state to manage a confined version
     */
    interface Unconfined: OutputState

    fun onResultAdded(result: Mapping)

    fun onResultRemoved(result: Mapping)

    companion object {

        operator fun invoke(context: QueryContext, ast: SelectQueryStructure): OutputState {
            val ordering = ast.ordering
            val ordered = when {
                ordering != null -> {
                    UnconfinedOrderedOutputState(comparator = OrderComparator(context, ordering))
                }
                else -> {
                    UnconfinedOutputState()
                }
            }
            return if (ast.limit != Int.MAX_VALUE || ast.offset != 0) {
                ConfinedOutputState(
                    inner = ordered,
                    offset = ast.offset,
                    limit = ast.limit,
                )
            } else {
                ordered
            }
        }
    }

}
