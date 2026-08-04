package dev.tesserakt.sparql.evaluation

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
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

    // a set, as duplicate insertions / deletions are not possible, and opposite changes can be found efficiently
    private val queue = mutableSetOf<DataDelta>()

    // we construct our listener, but only attach it after processing initial state, which we only do after having
    //  been called to update for the first time
    private val listener = object: ObservableStore.Listener {

        override fun onQuadAddedEncoded(quad: EncodedQuad) {
            process(DataAddition(quad))
        }

        override fun onQuadRemovedEncoded(quad: EncodedQuad) {
            process(DataDeletion(quad))
        }

    }

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
            // we can now also register our listener, so data changes since our initial state can be processed
            parent.addListener(listener)
            // we don't need to check the queue at this point, we do not support concurrent use, so the queue cannot
            //  possibly have elements inside
            return new
        }
        // we have a prior state that needs to be updated
        val iter = queue.iterator()
        while (iter.hasNext()) {
            state.process(iter.next())
        }
        queue.clear()
        return state
    }

    override fun close() {
        // we were never initialized, so the listener doesn't have to be removed
        if (state == null) {
            return
        }
        parent.removeListener(listener)
    }

    private fun process(change: DataDelta) {
        // if the other operation is already queued, we can remove it instead of inserting the new change
        if (queue.remove(change.inverse())) {
            return
        }
        queue.add(change)
    }

    private fun DataDelta.inverse() = when (this) {
        is DataAddition -> DataDeletion(this.value)
        is DataDeletion -> DataAddition(this.value)
    }

}
