package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.query.QueryState


internal class OngoingQueryEvaluationImpl<RT>(
    private val parent: ObservableStore,
    private val query: QueryState<RT, *>,
): OngoingQueryEvaluation<RT> {

    override val results get() = query.results

    private val listener = object: ObservableStore.Listener {

        override fun onQuadAddedEncoded(quad: EncodedQuad) {
            add(quad)
        }

        override fun onQuadRemovedEncoded(quad: EncodedQuad) {
            remove(quad)
        }

    }

    init {
        // our state is immediately initialized and is valid, so we only have to register our listener
        parent.addListener(listener)
    }

    override fun close() {
        parent.removeListener(listener)
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
