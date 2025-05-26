package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad

interface OngoingQueryEvaluation<RT> {

    val results: Collection<RT>

    fun subscribe(store: ObservableStore)

    fun unsubscribe(store: ObservableStore)

    fun add(quad: Quad)

    fun remove(quad: Quad)

    fun debugInformation(): String

}
