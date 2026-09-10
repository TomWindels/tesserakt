package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.query.QueryState


internal class DeferredOngoingQueryEvaluationImpl<RT>(
    private val parent: ObservableStore,
    // we keep the query in its stateless version here for now, as we want to defer creating the state as long as
    //  possible
    private val query: Query<RT>,
): DeferredOngoingQueryEvaluation<RT> {

    override val results: Collection<RT>
        get() {
            return updateAndGet().results
        }

    // we construct our listener, but only attach it after processing initial state, which we only do after having
    //  been called to update for the first time
    private class Listener<RT>(private val state: QueryState<RT, *>): ObservableStore.Listener {

        override fun onQuadAddedEncoded(quad: EncodedQuad) {
            state.enqueue(DataAddition(quad))
        }

        override fun onQuadRemovedEncoded(quad: EncodedQuad) {
            state.enqueue(DataDeletion(quad))
        }

    }

    // the active listener - deferred until the very first `results` request is made
    private var listener: Listener<RT>? = null

    override fun stats(granularity: QueryStatistics.Granularity): Statistics {
        return updateAndGet().stats(granularity)
    }

    private var state: QueryState<RT, *>? = null

    /**
     * Updates the internal state (creating it if necessary)
     */
    private fun updateAndGet(): QueryState<RT, *> {
        val state = state ?: run {
            // we have no initial state, so we initialize it here with our most up to date version
            val new = query.createState(parent)
            // we reuse this state, so we do actual incremental evaluation
            this.state = new
            val listener = Listener(new)
            // we can now also register our listener, so data changes since our initial state can be processed
            parent.addListener(listener)
            this.listener = listener
            // we don't need to check the queue at this point, we do not support concurrent use, so the queue cannot
            //  possibly have elements inside
            return new
        }
        // in case any changes were enqueued, since the last request, we process them here
        state.process()
        return state
    }

    override fun close() {
        // we were never initialized, so the listener doesn't have to be removed
        val listener = listener ?: return
        parent.removeListener(listener)
    }

}
