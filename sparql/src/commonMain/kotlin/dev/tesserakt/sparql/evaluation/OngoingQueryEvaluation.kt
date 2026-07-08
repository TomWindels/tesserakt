package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.runtime.evaluation.Statistics

interface OngoingQueryEvaluation<RT> {

    val results: Collection<RT>

    fun subscribe(store: ObservableStore)

    fun unsubscribe(store: ObservableStore)

    fun stats(): Statistics

}
