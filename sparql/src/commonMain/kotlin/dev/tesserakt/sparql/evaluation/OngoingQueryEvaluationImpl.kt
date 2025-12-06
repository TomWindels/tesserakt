package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.query.QueryState


internal class OngoingQueryEvaluationImpl<RT>(private val query: QueryState<RT, *>): OngoingQueryEvaluation<RT> {

    override val results get() = query.results

    private val listener = object: ObservableStore.Listener {
        override fun onQuadAdded(quad: Quad) {
            add(quad)
        }

        override fun onQuadRemoved(quad: Quad) {
            remove(quad)
        }
    }

    override fun subscribe(store: ObservableStore) {
        store.forEach { quad ->
            query.process(DataAddition(quad))
        }
        store.addListener(listener)
    }

    override fun unsubscribe(store: ObservableStore) {
        store.removeListener(listener)
    }

    override fun debugInformation(): String {
        return query.debugInformation()
    }

    private fun add(quad: Quad) {
        query.process(DataAddition(quad))
    }

    private fun remove(quad: Quad) {
        query.process(DataDeletion(quad))
    }

}
