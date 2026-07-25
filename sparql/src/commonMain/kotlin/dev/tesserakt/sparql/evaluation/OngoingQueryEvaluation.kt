package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.QueryStatistics

interface OngoingQueryEvaluation<RT> {

    val results: Collection<RT>

    fun subscribe(store: ObservableStore)

    fun unsubscribe(store: ObservableStore)

    fun stats(granularity: QueryStatistics.Granularity = QueryStatistics.Granularity.DETAILED): QueryStatistics

}
