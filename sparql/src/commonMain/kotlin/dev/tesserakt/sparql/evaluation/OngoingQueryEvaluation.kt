package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore

interface OngoingQueryEvaluation<RT> {

    val results: Collection<RT>

    fun subscribe(store: ObservableStore)

    fun unsubscribe(store: ObservableStore)

    fun debugInformation(): String

}
