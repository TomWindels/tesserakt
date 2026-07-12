package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.query.QueryState


internal class OngoingQueryEvaluationImpl<RT>(private val query: QueryState<RT, *>): OngoingQueryEvaluation<RT> {

    override val results get() = query.results

    private val listener = object: ObservableStore.Listener {
        override fun onQuadAddedEncoded(quad: EncodedQuad) {
            add(quad)
        }

        override fun onQuadRemovedEncoded(quad: EncodedQuad) {
            remove(quad)
        }
    }

    override fun subscribe(store: ObservableStore) {
        store.encodedIterator().forEach { quad ->
            query.process(DataAddition(quad))
        }
        store.addListener(listener)
    }

    override fun unsubscribe(store: ObservableStore) {
        store.removeListener(listener)
    }

    override fun stats(granularity: QueryStatistics.Granularity): Statistics {
        return query.stats(granularity)
    }

    private fun add(quad: EncodedQuad) {
        query.process(DataAddition(quad))
    }

    private fun remove(quad: EncodedQuad) {
        query.process(DataDeletion(quad))
    }

}
