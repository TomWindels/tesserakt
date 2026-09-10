package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.QueryStatistics

/**
 * An ongoing query evaluation, queuing up changes made to attached [ObservableStore]s until [results] are
 *  requested.
 *
 * IMPORTANT: You need to call [AutoCloseable.close] if you no longer want to listen to data changes. Failing to do so
 *  will leak this evaluation instance in the store it is attached to, meaning that data changes made to that store are
 *  also buffered here.
 */
interface DeferredOngoingQueryEvaluation<RT>: AutoCloseable {

    /**
     * The results of this query evaluation.
     *
     * IMPORTANT: requesting the value can be expensive if the underlying store(s) have changed dramatically
     *  compared to previous versions.
     */
    val results: Collection<RT>

    fun stats(granularity: QueryStatistics.Granularity = QueryStatistics.Granularity.DETAILED): QueryStatistics

}
