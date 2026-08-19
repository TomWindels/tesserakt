package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.QueryStatistics

/**
 * An ongoing query evaluation, applying changes made to its associated [ObservableStore] eagerly.
 *
 * IMPORTANT: You need to call [AutoCloseable.close] if you no longer want to listen to data changes. Failing to do so
 *  will leak this evaluation instance in the store it is attached to, meaning that data changes made to that store are
 *  also buffered here.
 */
interface OngoingQueryEvaluation<RT> : AutoCloseable {

    val results: Collection<RT>

    fun stats(granularity: QueryStatistics.Granularity = QueryStatistics.Granularity.DETAILED): QueryStatistics

}
