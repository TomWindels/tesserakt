package dev.tesserakt.sparql

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

interface OngoingQueryEvaluation<RT> {

    val results: Collection<RT>

    fun subscribe(store: MutableStore)

    fun unsubscribe(store: MutableStore)

    fun add(quad: Quad)

    fun remove(quad: Quad)

    fun debugInformation(): String

}
